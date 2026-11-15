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

/** Opens a genuine schema-7 database through the complete migration chain on the app's native driver. */
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
    fun aSchema7DatabaseMigratesTo9WithItsHistoryIndicesAndNewStorageColumns() =
        runTest {
            val entry =
                HistoryEntry(
                    key = key,
                    title = "Hi",
                    lastVisitedMillis = 9L,
                    lastReadPostId = PostId(7),
                    lastReadOffsetPx = 31,
                )
            createSchema7(entry)
            val migrated = open(*migrations())
            try {
                assertEquals(entry, HistoryRepositoryImpl(migrated.historyDao()).getEntry(key))
                val indices = migrated.readStrings("SELECT name FROM sqlite_master WHERE type = 'index'")
                assertTrue(indices.containsAll(SCHEMA_8_INDEX_NAMES), "indices after migrating: $indices")
                val version =
                    migrated.useReaderConnection { connection ->
                        connection.usePrepared("PRAGMA user_version") { statement ->
                            assertTrue(statement.step())
                            statement.getLong(0)
                        }
                    }
                assertEquals(9L, version)
                for ((table, expected) in SCHEMA_9_COLUMNS) {
                    val actual = migrated.readStrings("PRAGMA table_info(`$table`)", column = 1)
                    assertTrue(actual.containsAll(expected), "$table columns after migrating: $actual")
                }
            } finally {
                migrated.close()
            }
        }

    private fun createSchema7(entry: HistoryEntry) {
        val connection = BundledSQLiteDriver().open(path)
        try {
            Schema7DatabaseFixture.statements.forEach(connection::execSQL)
            val insert =
                connection.prepare(
                    "INSERT INTO history(provider, board, thread, title, thumbnailUrl, " +
                        "lastVisitedMillis, lastReadPostId, lastReadOffsetPx) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                )
            try {
                insert.bindText(1, entry.key.provider.value)
                insert.bindText(2, entry.key.board.value)
                insert.bindLong(3, entry.key.thread.value)
                insert.bindText(4, entry.title)
                insert.bindNull(5)
                insert.bindLong(6, entry.lastVisitedMillis)
                insert.bindLong(7, checkNotNull(entry.lastReadPostId).value)
                insert.bindLong(8, entry.lastReadOffsetPx.toLong())
                insert.step()
            } finally {
                insert.close()
            }
        } finally {
            connection.close()
        }
    }

    private suspend fun OrbinDatabase.readStrings(
        sql: String,
        column: Int = 0,
    ): Set<String> =
        useReaderConnection { connection ->
            connection.usePrepared(sql) { statement ->
                buildSet { while (statement.step()) add(statement.getText(column)) }
            }
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
        val SCHEMA_9_COLUMNS =
            mapOf(
                "bookmarks" to setOf("availability", "statusCheckedAtMillis"),
                "downloads" to setOf("mediaContextJson", "privateTarget"),
                "thread_memory" to setOf("provider", "board", "thread", "updatedAtMillis", "payload"),
                "offline_thread_copies" to
                    setOf("provider", "board", "thread", "payload", "updatedAtMillis", "sizeBytes"),
                "media_library" to setOf("id", "provider", "storageKey", "fileName", "savedAtMillis", "payload"),
            )

        val SCHEMA_8_INDEX_NAMES =
            listOf(
                "index_bookmarks_createdAtMillis",
                "index_bookmarks_isWatched",
                "index_history_lastVisitedMillis",
                "index_downloads_createdAtMillis",
            )
    }
}
