package com.orbin.data.database.entity

import androidx.room.Entity

/**
 * Room entities for locally-persisted state. Threads are keyed by the (provider, board, thread)
 * triple, mirroring [com.orbin.core.model.ThreadKey], so bookmarks/history survive process death
 * and work offline.
 */

@Entity(tableName = "bookmarks", primaryKeys = ["provider", "board", "thread"])
data class BookmarkEntity(
    val provider: String,
    val board: String,
    val thread: Long,
    val title: String,
    val thumbnailUrl: String?,
    val createdAtMillis: Long,
    val isWatched: Boolean,
    val lastSeenReplyCount: Int,
    val latestReplyCount: Int,
    val isThreadDead: Boolean,
)

@Entity(tableName = "history", primaryKeys = ["provider", "board", "thread"])
data class HistoryEntity(
    val provider: String,
    val board: String,
    val thread: Long,
    val title: String,
    val thumbnailUrl: String?,
    val lastVisitedMillis: Long,
    val lastReadPostId: Long?,
    val lastReadOffsetPx: Int = 0,
)

/** Lightweight projection of [HistoryEntity]'s key columns, for "has this thread been visited" checks. */
data class HistoryKeyRow(
    val provider: String,
    val board: String,
    val thread: Long,
)

/**
 * A provider's board list, cached so it survives process death and is readable offline.
 *
 * [sortIndex] preserves the order the provider returned — board lists are curated, not
 * alphabetical, and re-sorting them locally would show a different list than the site does.
 * [cachedAtMillis] is uniform across a provider's rows because they are always written as one
 * batch, and is what the repository ages out against.
 */
@Entity(tableName = "boards", primaryKeys = ["provider", "id"])
data class BoardEntity(
    val provider: String,
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val isNsfw: Boolean,
    val pageCount: Int?,
    val bumpLimit: Int?,
    val imageLimit: Int?,
    val maxCommentChars: Int?,
    val supportsMedia: Boolean,
    val sortIndex: Int,
    val cachedAtMillis: Long,
)

@Entity(tableName = "recent_searches", primaryKeys = ["provider", "query"])
data class RecentSearchEntity(
    val provider: String,
    val query: String,
    val lastUsedMillis: Long,
)

@Entity(tableName = "downloads", primaryKeys = ["id"])
data class DownloadEntity(
    /** Platform download manager id. */
    val id: Long,
    val url: String,
    val fileName: String,
    /** [com.orbin.core.model.DownloadStatus] name. */
    val status: String,
    val createdAtMillis: Long,
    /**
     * The subfolder path (if any) this file was saved under, e.g. "g/123456 - Thread title/".
     * Recorded at enqueue time so a retry lands in the same place even if the download
     * organization setting changes in between.
     */
    val relativeDir: String = "",
)
