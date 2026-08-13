package com.orbin.data.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.orbin.data.database.entity.HistoryEntity
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "encrypted-open-test.db"
private const val PASSPHRASE_BYTES = 32

/**
 * Covers the encrypted open path end-to-end, on a device, because nothing else can: SQLCipher's
 * native library does not load under Robolectric, so a JVM test cannot open a real encrypted file.
 *
 * This is the regression suite for the 82-Alioth crash, where the passphrase array was zeroed
 * before SQLCipher ever read it. Every assertion here would have failed on that build:
 * [reopeningWithTheSamePassphraseKeepsTheData] is the crash itself, and the recovery tests cover
 * the databases it left behind.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** A fresh array per call — the whole point is that the database must not mutate the caller's. */
    private fun passphrase() = ByteArray(PASSPHRASE_BYTES) { (it + 1).toByte() }

    /** What 82-Alioth actually keyed its databases with. */
    private fun legacyZeroedPassphrase() = ByteArray(PASSPHRASE_BYTES)

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    /** The production wiring from `DatabaseModule`, pointed at a throwaway database name. */
    private fun open(key: ByteArray): OrbinDatabase =
        Room
            .databaseBuilder(context, OrbinDatabase::class.java, TEST_DB)
            .openHelperFactory(
                SelfHealingOpenHelperFactory(
                    delegate = SupportOpenHelperFactory(key),
                    repair = DatabaseKeyRepair(context, key, TEST_DB)::repair,
                ),
            ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
            .build()

    private fun entry(thread: Long) =
        HistoryEntity(
            provider = "fourchan",
            board = "g",
            thread = thread,
            title = "A thread",
            thumbnailUrl = null,
            lastVisitedMillis = 1_000L,
            lastReadPostId = null,
        )

    private fun OrbinDatabase.writeEntry(thread: Long) = runBlocking { historyDao().upsert(entry(thread)) }

    private fun OrbinDatabase.readEntry(thread: Long) = runBlocking { historyDao().getEntry("fourchan", "g", thread) }

    @Test
    fun reopeningWithTheSamePassphraseKeepsTheData() {
        val first = open(passphrase())
        first.writeEntry(1)
        first.close()

        // 82-Alioth threw SQLiteNotADatabaseException here: by open time the array was all zeros.
        val second = open(passphrase())
        try {
            assertThat(second.readEntry(1)).isNotNull()
        } finally {
            second.close()
        }
    }

    @Test
    fun aSecondConnectionOpensWhileTheFirstIsStillOpen() {
        val database = open(passphrase())
        try {
            database.writeEntry(1)
            // SQLCipher re-reads the passphrase array for every connection the pool opens, so a
            // concurrent reader is a second chance to catch a passphrase that has been cleared.
            val reads = (1..8).map { runBlocking { database.historyDao().getEntry("fourchan", "g", 1) } }

            assertThat(reads.filterNotNull()).hasSize(8)
        } finally {
            database.close()
        }
    }

    @Test
    fun aDatabaseKeyedWithTheLegacyZeroedPassphraseIsRekeyedRatherThanWiped() {
        // Stand in for a database created by versionCode 102/103.
        val legacy = open(legacyZeroedPassphrase())
        legacy.writeEntry(42)
        legacy.close()

        val recovered = open(passphrase())
        try {
            // The user's history survives the recovery — this is the point of the rekey.
            assertThat(recovered.readEntry(42)).isNotNull()
        } finally {
            recovered.close()
        }

        // And the rekey persisted: the real passphrase now opens it with no repair needed.
        val reopened = open(passphrase())
        try {
            assertThat(reopened.readEntry(42)).isNotNull()
        } finally {
            reopened.close()
        }
    }

    @Test
    fun aFileThatIsNotADatabaseIsRecreatedInsteadOfCrashing() {
        context.getDatabasePath(TEST_DB).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(4096) { 0x41 })
        }

        val database = open(passphrase())
        try {
            // Losing an unreadable cache is recoverable; crash-looping on every launch is not.
            assertThat(database.readEntry(1)).isNull()
            database.writeEntry(1)
            assertThat(database.readEntry(1)).isNotNull()
        } finally {
            database.close()
        }
    }
}
