package com.orbin.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Newest SDK Robolectric 4.16 ships an image for; the app's target SDK is ahead of it. */
private const val ROBOLECTRIC_SDK = 35

/**
 * SQLCipher signals "this file will not open under the current key" by throwing
 * [SQLiteNotADatabaseException] from the lazy open, which is what
 * [SelfHealingOpenHelper] has to turn into a repair-and-retry instead of a crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class SelfHealingOpenHelperTest {
    private val database = mockk<SupportSQLiteDatabase>(relaxed = true)

    @Test
    fun opensWithoutRepairingWhenTheDatabaseIsReadable() {
        var repairs = 0
        val helper = helperOver(FakeOpenHelper(database), repair = { repairs++ })

        assertThat(helper.writableDatabase).isSameInstanceAs(database)
        assertThat(repairs).isEqualTo(0)
    }

    @Test
    fun repairsAndRetriesOnceWhenTheDatabaseWillNotOpen() {
        var repairs = 0
        val unopenable = FakeOpenHelper(database = null)
        val helper = helperOver(unopenable, FakeOpenHelper(database), repair = { repairs++ })

        assertThat(helper.writableDatabase).isSameInstanceAs(database)
        assertThat(repairs).isEqualTo(1)
        // The helper whose pool is bound to the pre-repair file must not be reused afterwards.
        assertThat(unopenable.closed).isTrue()
    }

    @Test
    fun readableDatabaseRecoversTheSameWay() {
        var repairs = 0
        val helper = helperOver(FakeOpenHelper(database = null), FakeOpenHelper(database), repair = { repairs++ })

        assertThat(helper.readableDatabase).isSameInstanceAs(database)
        assertThat(repairs).isEqualTo(1)
    }

    @Test
    fun failsInsteadOfRepairingInALoopWhenTheRepairDoesNotHelp() {
        var repairs = 0
        val helper =
            helperOver(
                FakeOpenHelper(database = null),
                FakeOpenHelper(database = null),
                FakeOpenHelper(database),
                repair = { repairs++ },
            )

        runCatching { helper.writableDatabase }.let { assertThat(it.exceptionOrNull()).isInstanceOf(NOT_A_DATABASE) }
        assertThat(repairs).isEqualTo(1)
    }

    @Test
    fun replaysWriteAheadLoggingOntoTheReplacementHelper() {
        val replacement = FakeOpenHelper(database)
        val helper = helperOver(FakeOpenHelper(database = null), replacement, repair = {})

        helper.setWriteAheadLoggingEnabled(true)
        helper.writableDatabase

        assertThat(replacement.writeAheadLoggingEnabled).isTrue()
    }

    private fun helperOver(
        vararg delegates: SupportSQLiteOpenHelper,
        repair: () -> Unit,
    ): SelfHealingOpenHelper {
        val remaining = delegates.toMutableList()
        return SelfHealingOpenHelper({ remaining.removeAt(0) }, repair)
    }

    private companion object {
        val NOT_A_DATABASE = SQLiteNotADatabaseException::class.java
    }
}

/** Opens to [database], or fails the way SQLCipher does when the key does not match the file. */
private class FakeOpenHelper(
    private val database: SupportSQLiteDatabase?,
) : SupportSQLiteOpenHelper {
    var closed = false
        private set
    var writeAheadLoggingEnabled: Boolean? = null
        private set

    override val databaseName: String = "orbin.db"

    override val writableDatabase: SupportSQLiteDatabase
        get() = database ?: throw SQLiteNotADatabaseException("file is not a database (code 26)")

    override val readableDatabase: SupportSQLiteDatabase
        get() = writableDatabase

    override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
        writeAheadLoggingEnabled = enabled
    }

    override fun close() {
        closed = true
    }
}
