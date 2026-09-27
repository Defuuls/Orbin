package com.orbin.data.database

/**
 * SQL changes for every supported Room schema upgrade.
 *
 * Kept in the shared storage module so Android and iOS apply the same schema changes. Platform
 * builders adapt these statements to Room's platform-specific migration callback.
 */
data class OrbinSchemaMigration(
    val startVersion: Int,
    val endVersion: Int,
    val statements: List<String>,
)

object OrbinSchemaMigrations {
    val all: List<OrbinSchemaMigration> =
        listOf(
            OrbinSchemaMigration(
                startVersion = 2,
                endVersion = 3,
                statements =
                    listOf(
                        "CREATE TABLE IF NOT EXISTS `saved_searches` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`text` TEXT NOT NULL, " +
                            "`board` TEXT, " +
                            "`mediaOnly` INTEGER NOT NULL, " +
                            "`minReplies` INTEGER, " +
                            "`includeNsfw` INTEGER NOT NULL, " +
                            "`contentTypes` TEXT NOT NULL, " +
                            "`createdAtMillis` INTEGER NOT NULL)",
                    ),
            ),
            OrbinSchemaMigration(
                startVersion = 3,
                endVersion = 4,
                statements =
                    listOf(
                        "ALTER TABLE `history` ADD COLUMN `lastReadOffsetPx` INTEGER NOT NULL DEFAULT 0",
                    ),
            ),
            OrbinSchemaMigration(
                startVersion = 4,
                endVersion = 5,
                statements =
                    listOf(
                        "ALTER TABLE `downloads` ADD COLUMN `relativeDir` TEXT NOT NULL DEFAULT ''",
                    ),
            ),
            OrbinSchemaMigration(
                startVersion = 5,
                endVersion = 6,
                statements =
                    listOf(
                        "CREATE TABLE IF NOT EXISTS `boards` (" +
                            "`provider` TEXT NOT NULL, " +
                            "`id` TEXT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`description` TEXT NOT NULL, " +
                            "`category` TEXT NOT NULL, " +
                            "`isNsfw` INTEGER NOT NULL, " +
                            "`pageCount` INTEGER, " +
                            "`bumpLimit` INTEGER, " +
                            "`imageLimit` INTEGER, " +
                            "`maxCommentChars` INTEGER, " +
                            "`supportsMedia` INTEGER NOT NULL, " +
                            "`sortIndex` INTEGER NOT NULL, " +
                            "`cachedAtMillis` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`provider`, `id`))",
                    ),
            ),
            OrbinSchemaMigration(
                startVersion = 6,
                endVersion = 7,
                statements =
                    listOf(
                        "CREATE TABLE IF NOT EXISTS `saved_threads` (" +
                            "`provider` TEXT NOT NULL, " +
                            "`board` TEXT NOT NULL, " +
                            "`thread` INTEGER NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`savedAtMillis` INTEGER NOT NULL, " +
                            "`postCount` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`provider`, `board`, `thread`))",
                        "CREATE TABLE IF NOT EXISTS `saved_posts` (" +
                            "`provider` TEXT NOT NULL, " +
                            "`board` TEXT NOT NULL, " +
                            "`thread` INTEGER NOT NULL, " +
                            "`postId` INTEGER NOT NULL, " +
                            "`isOriginalPost` INTEGER NOT NULL, " +
                            "`subject` TEXT, " +
                            "`comment` TEXT NOT NULL, " +
                            "`posterName` TEXT, " +
                            "`posterTripcode` TEXT, " +
                            "`posterIdentifier` TEXT, " +
                            "`posterCapcode` TEXT, " +
                            "`createdAtMillis` INTEGER NOT NULL, " +
                            "`sortIndex` INTEGER NOT NULL, " +
                            "`attachmentUrls` TEXT NOT NULL, " +
                            "`attachmentNames` TEXT NOT NULL, " +
                            "PRIMARY KEY(`provider`, `board`, `thread`, `postId`))",
                    ),
            ),
            OrbinSchemaMigration(
                startVersion = 7,
                endVersion = 8,
                statements =
                    listOf(
                        "CREATE INDEX IF NOT EXISTS `index_bookmarks_createdAtMillis` ON `bookmarks` (`createdAtMillis`)",
                        "CREATE INDEX IF NOT EXISTS `index_bookmarks_isWatched` ON `bookmarks` (`isWatched`)",
                        "CREATE INDEX IF NOT EXISTS `index_history_lastVisitedMillis` ON `history` (`lastVisitedMillis`)",
                        "CREATE INDEX IF NOT EXISTS `index_downloads_createdAtMillis` ON `downloads` (`createdAtMillis`)",
                    ),
            ),
        )
}
