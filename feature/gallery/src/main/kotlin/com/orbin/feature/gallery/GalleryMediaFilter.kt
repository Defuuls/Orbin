package com.orbin.feature.gallery

import com.orbin.uinext.components.MediaKindFilter
import com.orbin.uinext.components.mediaFilterIndices
import com.orbin.uinext.components.mediaKindOf
import kotlinx.collections.immutable.toImmutableList

/** Filtering changes displayed positions, never canonical file identities or parent-thread indices. */
internal fun GalleryContentState.Ready.withMediaFilter(filter: MediaKindFilter): GalleryContentState.Ready {
    if (filter == MediaKindFilter.ALL) return this
    val indices = mediaFilterIndices(media.map { mediaKindOf(it.type.name) }, filter)
    return copy(
        media = indices.map(media::get).toImmutableList(),
        threadMediaIndices = indices.map(threadMediaIndices::get).toImmutableList(),
        pageKeys = indices.map(pageKeys::get).toImmutableList(),
    )
}
