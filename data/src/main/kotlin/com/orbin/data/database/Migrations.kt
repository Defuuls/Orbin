package com.orbin.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit schema migrations.
 *
 * The database previously relied on `fallbackToDestructiveMigration()`, justified in a comment as
 * pre-1.0 churn with "real migrations land before release". Sixty releases later that premise had
 * expired, and the next schema bump would have silently dropped every table — bookmarks, history,
 * downloads, recent searches, saved searches — with no Android backup to recover from.
 *
 * Adding a version now means adding a [Migration] here. The exported schema JSON under
 * `data/schemas` is the source of truth for what changed between two versions.
 */
internal val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // v3 added saved searches. Statement copied from the exported v3 schema so the result
            // is byte-identical to a freshly created database, which is what Room validates.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_searches` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`text` TEXT NOT NULL, " +
                    "`board` TEXT, " +
                    "`mediaOnly` INTEGER NOT NULL, " +
                    "`minReplies` INTEGER, " +
                    "`includeNsfw` INTEGER NOT NULL, " +
                    "`contentTypes` TEXT NOT NULL, " +
                    "`createdAtMillis` INTEGER NOT NULL)",
            )
        }
    }

internal val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // v4 adds a scroll-restore offset alongside history's existing lastReadPostId anchor.
            db.execSQL(
                "ALTER TABLE `history` ADD COLUMN `lastReadOffsetPx` INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

internal val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // v5 records the subfolder (if any) a download was saved under, so a retry lands in
            // the same place even if the download organization setting changes in between.
            db.execSQL(
                "ALTER TABLE `downloads` ADD COLUMN `relativeDir` TEXT NOT NULL DEFAULT ''",
            )
        }
    }

internal val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // v6 caches each provider's board list so it survives process death and reads offline.
            // Statement copied from the exported v6 schema so the result is byte-identical to a
            // freshly created database, which is what Room validates. Nothing is backfilled: an
            // empty table reads as "never cached" and the first observer refreshes it.
            db.execSQL(
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
            )
        }
    }

internal val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // v7 lets a reader keep a thread's text, so a thread pruned upstream survives locally.
            // Statements copied from the exported v7 schema so the result is byte-identical to a
            // freshly created database, which is what Room validates.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_threads` (" +
                    "`provider` TEXT NOT NULL, " +
                    "`board` TEXT NOT NULL, " +
                    "`thread` INTEGER NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`savedAtMillis` INTEGER NOT NULL, " +
                    "`postCount` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`provider`, `board`, `thread`))",
            )
            db.execSQL(
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
            )
        }
    }
