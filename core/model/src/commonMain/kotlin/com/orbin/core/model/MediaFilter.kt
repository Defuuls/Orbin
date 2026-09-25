package com.orbin.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

/**
 * Which kinds of attachment the browsing views show. Applies everywhere post media is displayed —
 * the subscribed feed, board catalogs, thread view and the galleries — so a reader who only wants
 * webms (or only pictures) gets that consistently rather than per screen.
 *
 * Audio and unrecognised files are attachments of neither kind, so they are hidden while either
 * filter is on: "only videos" means videos, not "everything that isn't a picture".
 */
@Serializable
enum class MediaFilter(
    val label: String,
) {
    ALL("All media"),
    IMAGES("Images only"),
    VIDEOS("Videos only"),
    ;

    /** True when this filter actually removes something, i.e. anything other than [ALL]. */
    val isActive: Boolean get() = this != ALL

    fun allows(type: MediaType): Boolean =
        when (this) {
            ALL -> true
            // GIFs and APNGs are pictures to a reader, whatever the container says.
            IMAGES -> type == MediaType.IMAGE || type == MediaType.ANIMATED_IMAGE
            VIDEOS -> type == MediaType.VIDEO
        }

    fun allows(attachment: MediaAttachment): Boolean = allows(attachment.type)
}

/** The attachments this filter keeps, in their original order. */
fun ImmutableList<MediaAttachment>.filteredBy(filter: MediaFilter): ImmutableList<MediaAttachment> =
    if (!filter.isActive) this else this.filter { filter.allows(it) }.toImmutableList()

/** The attachments this filter keeps, in their original order. */
fun List<MediaAttachment>.filteredBy(filter: MediaFilter): List<MediaAttachment> =
    if (!filter.isActive) this else this.filter { filter.allows(it) }

/** The post with only the attachments [filter] keeps; its text is untouched. */
fun Post.filteredBy(filter: MediaFilter): Post =
    if (!filter.isActive) this else copy(attachments = attachments.filteredBy(filter))

/**
 * The thread with every post's attachments filtered. Posts themselves are kept even when their
 * media is filtered out — the thread view is a conversation, and dropping replies would leave the
 * quotes and backlinks between them pointing at nothing.
 */
fun Thread.filteredBy(filter: MediaFilter): Thread =
    if (!filter.isActive) {
        this
    } else {
        copy(
            originalPost = originalPost.filteredBy(filter),
            replies = replies.map { it.filteredBy(filter) }.toImmutableList(),
        )
    }

/** The catalog entry with its OP's attachments filtered; see [filteredCatalogBy] for hiding. */
fun CatalogThread.filteredBy(filter: MediaFilter): CatalogThread =
    if (!filter.isActive) this else copy(originalPost = originalPost.filteredBy(filter))

/**
 * Catalog entries as a filtered listing: each thread keeps only the media [filter] allows, and
 * threads left with none drop out entirely.
 *
 * Catalog and feed cells are a thumbnail of the OP's media, so a thread with nothing matching has
 * nothing to show. Hiding it is what makes "videos only" a browsable listing of videos instead of
 * a wall of placeholder tiles.
 */
fun List<CatalogThread>.filteredCatalogBy(filter: MediaFilter): List<CatalogThread> =
    if (!filter.isActive) {
        this
    } else {
        mapNotNull { thread ->
            thread.filteredBy(filter).takeIf { it.originalPost.attachments.isNotEmpty() }
        }
    }
