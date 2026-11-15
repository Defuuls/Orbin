package com.orbin.core.model

fun MediaAttachment.downloadContext(
    thread: ThreadKey,
    postId: Long? = null,
): DownloadMediaContext =
    DownloadMediaContext(
        providerId = thread.provider.value,
        boardId = thread.board.value,
        threadId = thread.thread.value,
        postId = postId,
        attachmentId = id,
        mediaType = type,
        mimeType = mediaMimeType(extension),
        width = width,
        height = height,
    )

fun mediaMimeType(extension: String): String =
    when (extension.lowercase().removePrefix(".")) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "apng" -> "image/apng"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }

/** Download ids are device-unique; provider metadata remains separate from the opaque byte key. */
fun DownloadMediaContext.libraryRecord(
    downloadId: Long,
    sourceUrl: String,
    fileName: String,
    byteCount: Long,
    savedAtMillis: Long,
): MediaLibraryRecord =
    MediaLibraryRecord(
        id = "download-$downloadId",
        providerId = providerId,
        boardId = boardId,
        threadId = threadId,
        postId = postId,
        attachmentId = attachmentId,
        sourceUrl = sourceUrl,
        fileName = fileName,
        mediaType = mediaType,
        mimeType = mimeType,
        byteCount = byteCount,
        savedAtMillis = savedAtMillis,
        storageKey = "download-$downloadId",
        width = width,
        height = height,
    )
