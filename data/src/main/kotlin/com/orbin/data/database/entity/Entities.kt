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
)
