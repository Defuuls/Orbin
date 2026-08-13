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

/**
 * A thread the reader chose to keep. Threads are pruned upstream — that is the defining property
 * of the platform — so a thread read an hour ago can be gone, and downloads only ever covered its
 * media. This keeps its text.
 */
@Entity(tableName = "saved_threads", primaryKeys = ["provider", "board", "thread"])
data class SavedThreadEntity(
    val provider: String,
    val board: String,
    val thread: Long,
    val title: String,
    val savedAtMillis: Long,
    val postCount: Int,
)

/**
 * One post of a [SavedThreadEntity], flattened to primitives.
 *
 * The comment is stored as its own text rather than the parsed node tree: parsing is a provider
 * concern and re-running it here would tie saved copies to the engine that produced them. A saved
 * post therefore reads as plain text, which is what an archive needs to be — attachment URLs are
 * kept so links survive, while the files themselves remain the job of "download all media".
 */
@Entity(tableName = "saved_posts", primaryKeys = ["provider", "board", "thread", "postId"])
data class SavedPostEntity(
    val provider: String,
    val board: String,
    val thread: Long,
    val postId: Long,
    val isOriginalPost: Boolean,
    val subject: String?,
    val comment: String,
    val posterName: String?,
    val posterTripcode: String?,
    val posterIdentifier: String?,
    val posterCapcode: String?,
    val createdAtMillis: Long,
    /** Reading order, so replies come back in the order they were posted. */
    val sortIndex: Int,
    /** Newline-separated, paired by position with [attachmentNames]. */
    val attachmentUrls: String,
    val attachmentNames: String,
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
