package com.orbin.data.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** The schema the iOS app first shipped the database at; every iOS copy starts here or later. */
const val IOS_FIRST_SCHEMA = 7

/** Version 8's indices on the columns bookmarks, history and downloads are sorted and filtered by. */
val SCHEMA_8_INDICES: List<String> =
    listOf(
        "CREATE INDEX IF NOT EXISTS `index_bookmarks_createdAtMillis` ON `bookmarks` (`createdAtMillis`)",
        "CREATE INDEX IF NOT EXISTS `index_bookmarks_isWatched` ON `bookmarks` (`isWatched`)",
        "CREATE INDEX IF NOT EXISTS `index_history_lastVisitedMillis` ON `history` (`lastVisitedMillis`)",
        "CREATE INDEX IF NOT EXISTS `index_downloads_createdAtMillis` ON `downloads` (`createdAtMillis`)",
    )

/**
 * The migrations the iOS app opens the database with, from [IOS_FIRST_SCHEMA] to
 * [OrbinDatabase.VERSION]. Written against Room's connection API, which is the only one the bundled
 * SQLite driver has; Android keeps its own chain over its encrypted open helper (`data`).
 *
 * Without a step here a schema bump crashes the iOS app on launch: Room refuses to open a database
 * it cannot migrate. `ConnectionMigrationsTest` fails when the chain stops short of the schema.
 */
val CONNECTION_MIGRATIONS: Array<Migration> =
    arrayOf(
        object : Migration(7, 8) {
            override fun migrate(connection: SQLiteConnection) {
                SCHEMA_8_INDICES.forEach(connection::execSQL)
            }
        },
    )
