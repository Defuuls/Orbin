package com.orbin.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Aggregate counts for a thread, shown in headers and used for unread tracking. */
data class ThreadStats(
    val replyCount: Int = 0,
    val imageCount: Int = 0,
    val uniquePosterCount: Int = 0,
    val isSticky: Boolean = false,
    val isClosed: Boolean = false,
    val isArchived: Boolean = false,
    /** Epoch millis the thread last bumped; used for catalog sorting. */
    val lastModifiedMillis: Long = 0,
)

/**
 * A full thread: its opening post plus all replies, in order. Built by the data layer from a
 * provider response and enriched with backlinks.
 */
data class Thread(
    val key: ThreadKey,
    val originalPost: Post,
    val replies: ImmutableList<Post> = persistentListOf(),
    val stats: ThreadStats = ThreadStats(),
) {
    /** OP followed by replies, the natural reading order. */
    val allPosts: List<Post> get() =
        buildList(replies.size + 1) {
            add(originalPost)
            addAll(replies)
        }

    val subject: String? get() = originalPost.subject
}

/**
 * A condensed thread as it appears in a board catalog: the OP plus stats and (optionally) a few
 * teaser replies. Distinct from [Thread] so the catalog stays lightweight for Paging.
 */
data class CatalogThread(
    val key: ThreadKey,
    val originalPost: Post,
    val stats: ThreadStats,
    /** A small preview of the latest replies, when the engine includes them in the catalog. */
    val previewReplies: ImmutableList<Post> = persistentListOf(),
)
