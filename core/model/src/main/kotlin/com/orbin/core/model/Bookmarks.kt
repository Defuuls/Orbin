package com.orbin.core.model

/**
 * A user bookmark / watched thread. When [isWatched] is true the background refresh worker polls
 * the thread and surfaces unread counts and notifications.
 */
data class Bookmark(
    val key: ThreadKey,
    val title: String,
    val thumbnailUrl: String? = null,
    val createdAtMillis: Long,
    val isWatched: Boolean = false,
    /** Reply count last time the thread was opened/refreshed, for unread math. */
    val lastSeenReplyCount: Int = 0,
    /** Latest known reply count from the most recent refresh. */
    val latestReplyCount: Int = 0,
    val isThreadDead: Boolean = false,
) {
    val unreadCount: Int get() = (latestReplyCount - lastSeenReplyCount).coerceAtLeast(0)
    val hasUnread: Boolean get() = unreadCount > 0
}

/** An entry in the local reading history. */
data class HistoryEntry(
    val key: ThreadKey,
    val title: String,
    val thumbnailUrl: String? = null,
    val lastVisitedMillis: Long,
    /** Scroll anchor (post id) to restore the reading position. */
    val lastReadPostId: PostId? = null,
)
