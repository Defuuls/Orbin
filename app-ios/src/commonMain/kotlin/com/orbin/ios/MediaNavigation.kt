package com.orbin.ios

import com.orbin.core.model.MediaAttachment

/** An absent explicit file is an error, never permission to show an unrelated first attachment. */
internal fun Route.Media.startIndexIn(files: List<MediaAttachment>): Int? {
    if (files.isEmpty()) return null
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
