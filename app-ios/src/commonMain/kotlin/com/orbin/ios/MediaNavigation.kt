package com.orbin.ios

import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.isPermanentlyFiltered

/** An absent explicit file is an error, never permission to show an unrelated first attachment. */
internal fun Route.Media.startIndexIn(files: List<MediaAttachment>): Int? {
    if (files.isEmpty()) return null
    if (initialAttachment != null) return initialMediaAttachment()?.let(files::indexOfViewerFile)
    val requested = attachmentId?.takeIf(String::isNotBlank)
    val exact = files.indexOfFirst { requested != null && it.id == requested }
    val preview = thumbnailUrl?.takeIf(String::isNotBlank)
    return when {
        attachmentId == null && thumbnailUrl == null -> index.coerceIn(files.indices)
        exact >= 0 -> exact
        preview != null -> files.indices.singleOrNull { files[it].thumbnailUrl == preview }
        else -> null
    }
}

/** A seed is an original attachment matching the explicit selection, never a guessed thumbnail. */
private fun Route.Media.initialMediaAttachment(): MediaAttachment? =
    initialAttachment?.takeIf { file ->
        attachmentId?.takeIf(String::isNotBlank) == file.id &&
            (thumbnailUrl == null || thumbnailUrl == file.thumbnailUrl) &&
            !file.isPermanentlyFiltered() &&
            safeExternalLink(file.sourceUrl) != null
    }

/**
 * Start the selected file immediately, then expand its playlist without replacing its page key.
 * Catalog and full-thread parsers may assign different IDs to the same original source URL.
 */
internal fun Route.Media.viewerFiles(loadedFiles: List<MediaAttachment>): List<MediaAttachment> {
    val initial = initialMediaAttachment() ?: return loadedFiles
    val selected = loadedFiles.indexOfViewerFile(initial)
    return if (selected == null) {
        listOf(initial) + loadedFiles
    } else {
        loadedFiles.mapIndexed { index, file -> if (index == selected) file.copy(id = initial.id) else file }
    }
}

/** IDs alone may be reused; a source URL always qualifies a selected or returning file. */
internal fun List<MediaAttachment>.indexOfViewerFile(file: MediaAttachment): Int? {
    val exact = indexOfFirst { it.id == file.id && it.sourceUrl == file.sourceUrl }
    return exact.takeIf { it >= 0 } ?: indices.singleOrNull { this[it].sourceUrl == file.sourceUrl }
}
