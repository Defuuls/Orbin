package com.orbin.app.navigation

import com.orbin.core.model.MediaAttachment
import com.orbin.feature.gallery.encodeGalleryMediaSeed

internal fun selectedMediaRoute(
    provider: String,
    board: String,
    thread: Long,
    attachment: MediaAttachment,
): Route {
    val seed = encodeGalleryMediaSeed(provider, board, thread, attachment)
    return if (seed != null) {
        Route.SeededMedia(provider, board, thread, attachment.id, seed, attachment.thumbnailUrl)
    } else {
        Route.SelectedMedia(provider, board, thread, attachment.id, attachment.thumbnailUrl)
    }
}
