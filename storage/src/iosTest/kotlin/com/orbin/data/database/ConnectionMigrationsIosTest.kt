package com.orbin.data.database

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.orbin.core.model.BoardId
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.data.repository.HistoryRepositoryImpl
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An iOS database written at schema 7 opens, keeps its rows and gains version 8's indices, over the
 * same bundled driver the app uses. Schema 8 only added indices, so a version-7 file is today's
 * file without them, stamped with version 7's number and identity.
 */
class ConnectionMigrationsIosTest {
    private val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".db"
    private val key = ThreadKey(ProviderId("p"), BoardId("g"), ThreadId(7))

    @OptIn(ExperimentalForeignApi::class)
    @AfterTest
    fun delete() {
        listOf("", "-wal", "-shm").forEach { suffix ->
            NSFileManager.defaultManager.removeItemAtPath(path + suffix, null)
        }
    }

    @Test
    fun aSchema7DatabaseOpensWithItsHistoryAndTheNewIndices() =
        runTest {
            val entry =
                HistoryEntry(key = key, title = "Hi", lastVisitedMillis = 9L, lastReadPostId = PostId(7))
            open().run {
                HistoryRepositoryImpl(historyDao()).record(entry)
                close()
            }
            BundledSQLiteDriver().open(path).run {
                SCHEMA_8_INDEX_NAMES.forEach { name -> execSQL("DROP INDEX `$name`") }
                execSQL("UPDATE room_master_table SET identity_hash = '$SCHEMA_7_IDENTITY' WHERE id = 42")
                execSQL("PRAGMA user_version = 7")
                close()
            }

            val migrated = open(*migrations())
            assertEquals(entry, HistoryRepositoryImpl(migrated.historyDao()).getEntry(key))
            val indices =
                migrated.useReaderConnection { connection ->
                    connection.usePrepared("SELECT name FROM sqlite_master WHERE type = 'index'") { statement ->
                        buildSet { while (statement.step()) add(statement.getText(0)) }
                    }
                }
            assertTrue(indices.containsAll(SCHEMA_8_INDEX_NAMES), "indices after migrating: $indices")
            migrated.close()
        }

    private fun open(vararg migrations: Migration): OrbinDatabase =
        Room
            .databaseBuilder<OrbinDatabase>(name = path)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*migrations)
            .build()

    /** The shared steps adapted to the connection API, as the iOS app's `openDatabase` does. */
    private fun migrations(): Array<Migration> =
        OrbinSchemaMigrations.all
            .map { step ->
                object : Migration(step.startVersion, step.endVersion) {
                    override fun migrate(connection: SQLiteConnection) {
                        step.statements.forEach { connection.execSQL(it) }
                    }
                }
            }.toTypedArray()

    private companion object {
        /** From `storage/schemas/.../7.json`. */
        const val SCHEMA_7_IDENTITY = "fe1533596a57805d02d3102fd81b0b24"

        val SCHEMA_8_INDEX_NAMES =
            listOf(
                "index_bookmarks_createdAtMillis",
                "index_bookmarks_isWatched",
                "index_history_lastVisitedMillis",
                "index_downloads_createdAtMillis",
            )
    }
}
