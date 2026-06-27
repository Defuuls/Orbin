package com.orbin.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Optional poster identity/flair metadata that varies a lot between engines. */
data class PosterInfo(
    val name: String? = null,
    val tripcode: String? = null,
    /** Per-thread poster id used by some boards to distinguish anonymous users. */
    val posterId: String? = null,
    val capcode: String? = null, // e.g. "Mod", "Admin"
    val countryCode: String? = null,
    val countryName: String? = null,
)

/**
 * A single post: either the opening post of a thread or a reply. This is the central content
 * entity and is designed to be immutable so it can flow through Paging and Compose unchanged.
 *
 * [backlinks] (posts that quote this one) are computed by the data layer after a thread loads,
 * because engines only give us forward quote links.
 */
data class Post(
    val id: PostId,
    val board: BoardId,
    val threadId: ThreadId,
    val isOriginalPost: Boolean,
    val subject: String? = null,
    val comment: PostComment = PostComment.Empty,
    val poster: PosterInfo = PosterInfo(),
    /** Epoch milliseconds the post was created. */
    val createdAtMillis: Long = 0,
    val attachments: ImmutableList<MediaAttachment> = persistentListOf(),
    /** Posts this one quotes (forward edges in the reply graph), derived from [comment]. */
    val repliesTo: ImmutableList<PostId> = persistentListOf(),
    /** Posts that quote this one (back edges), filled in by the data layer. */
    val backlinks: ImmutableList<PostId> = persistentListOf(),
) {
    val hasMedia: Boolean get() = attachments.isNotEmpty()
}
