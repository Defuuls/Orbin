package com.orbin.data.database

/**
 * Genuine schema-7 SQL generated from storage/schemas/com.orbin.data.database.OrbinDatabase/7.json.
 * Source SHA-256: e555137d7e59e3b6f4d935e436b7d58885bfc9f34bc50890ca2dbcd4388ff72e.
 * Kept in Kotlin because the native test runner does not package JVM classpath resources.
 */
internal object Schema7DatabaseFixture {
    val statements =
        listOf(
            "CREATE TABLE IF NOT EXISTS `bookmarks` (`provider` TEXT NOT NULL, `board` TEXT NOT " +
                "NULL, `thread` INTEGER NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT, " +
                "`createdAtMillis` INTEGER NOT NULL, `isWatched` INTEGER NOT NULL, " +
                "`lastSeenReplyCount` INTEGER NOT NULL, `latestReplyCount` INTEGER NOT NULL, " +
                "`isThreadDead` INTEGER NOT NULL, PRIMARY KEY(`provider`, `board`, `thread`))",
            "CREATE TABLE IF NOT EXISTS `history` (`provider` TEXT NOT NULL, `board` TEXT NOT " +
                "NULL, `thread` INTEGER NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT, " +
                "`lastVisitedMillis` INTEGER NOT NULL, `lastReadPostId` INTEGER, `lastReadOffsetPx` " +
                "INTEGER NOT NULL, PRIMARY KEY(`provider`, `board`, `thread`))",
            "CREATE TABLE IF NOT EXISTS `recent_searches` (`provider` TEXT NOT NULL, `query` TEXT" +
                " NOT NULL, `lastUsedMillis` INTEGER NOT NULL, PRIMARY KEY(`provider`, `query`))",
            "CREATE TABLE IF NOT EXISTS `downloads` (`id` INTEGER NOT NULL, `url` TEXT NOT NULL, " +
                "`fileName` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAtMillis` INTEGER NOT " +
                "NULL, `relativeDir` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `saved_searches` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                "NOT NULL, `text` TEXT NOT NULL, `board` TEXT, `mediaOnly` INTEGER NOT NULL, " +
                "`minReplies` INTEGER, `includeNsfw` INTEGER NOT NULL, `contentTypes` TEXT NOT NULL, " +
                "`createdAtMillis` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `boards` (`provider` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `description` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                "`isNsfw` INTEGER NOT NULL, `pageCount` INTEGER, `bumpLimit` INTEGER, `imageLimit` " +
                "INTEGER, `maxCommentChars` INTEGER, `supportsMedia` INTEGER NOT NULL, `sortIndex` " +
                "INTEGER NOT NULL, `cachedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`provider`, `id`))",
            "CREATE TABLE IF NOT EXISTS `saved_threads` (`provider` TEXT NOT NULL, `board` TEXT " +
                "NOT NULL, `thread` INTEGER NOT NULL, `title` TEXT NOT NULL, `savedAtMillis` INTEGER " +
                "NOT NULL, `postCount` INTEGER NOT NULL, PRIMARY KEY(`provider`, `board`, `thread`))",
            "CREATE TABLE IF NOT EXISTS `saved_posts` (`provider` TEXT NOT NULL, `board` TEXT NOT" +
                " NULL, `thread` INTEGER NOT NULL, `postId` INTEGER NOT NULL, `isOriginalPost` " +
                "INTEGER NOT NULL, `subject` TEXT, `comment` TEXT NOT NULL, `posterName` TEXT, " +
                "`posterTripcode` TEXT, `posterIdentifier` TEXT, `posterCapcode` TEXT, " +
                "`createdAtMillis` INTEGER NOT NULL, `sortIndex` INTEGER NOT NULL, `attachmentUrls` " +
                "TEXT NOT NULL, `attachmentNames` TEXT NOT NULL, PRIMARY KEY(`provider`, `board`, " +
                "`thread`, `postId`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash " +
                "TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, " +
                "'fe1533596a57805d02d3102fd81b0b24')",
            "PRAGMA user_version = 7",
        )
}
