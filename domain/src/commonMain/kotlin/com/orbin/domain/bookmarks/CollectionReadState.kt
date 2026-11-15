package com.orbin.domain.bookmarks

import com.orbin.core.model.Bookmark
import com.orbin.core.model.ThreadMemory

/** Exact loaded unread replies plus replies discovered since the last loaded snapshot. */
fun Bookmark.collectionUnread(memory: ThreadMemory?): Int {
    if (memory == null || memory.knownPostIds.isEmpty()) return unreadCount
    val knownReplies = memory.knownPostIds - key.thread.value
    val unseenLoaded = (knownReplies - memory.seenPostIds).size
    val unseenTail =
        (
            latestReplyCount -
                maxOf(
                    knownReplies.size,
                    lastSeenReplyCount,
                    memory.acknowledgedReplyCount,
                )
        ).coerceAtLeast(0)
    return unseenLoaded + unseenTail
}

/** History can show unread posts already loaded even without a bookmark counter. */
fun ThreadMemory.collectionUnread(): Int = (knownPostIds - seenPostIds - key.thread.value).size
