package com.orbin.feature.gallery

import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.isPermanentlyFiltered
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI

/** A small, persisted navigation argument; the selected original never waits for the thread request. */
@Serializable
private data class GalleryMediaSeed(
    val provider: String,
    val board: String,
    val thread: Long,
    val id: String,
    val originalFileName: String,
    val extension: String,
    val type: String,
    val sourceUrl: String,
    val thumbnailUrl: String,
    val width: Int,
    val height: Int,
    val thumbnailWidth: Int,
    val thumbnailHeight: Int,
    val sizeBytes: Long,
    val isSpoiler: Boolean,
    val durationMs: Long?,
)

/** Uses Navigation's supported String argument type, so URLs survive saved-stack restoration. */
fun encodeGalleryMediaSeed(
    provider: String,
    board: String,
    thread: Long,
    attachment: MediaAttachment,
): String? {
    if (!validSeedOwner(provider, board, thread) || !attachment.canSeedGallery()) return null
    return Json.encodeToString(
        GalleryMediaSeed(
            provider,
            board,
            thread,
            attachment.id,
            attachment.originalFileName,
            attachment.extension,
            attachment.type.name,
            attachment.sourceUrl,
            attachment.thumbnailUrl,
            attachment.width,
            attachment.height,
            attachment.thumbnailWidth,
            attachment.thumbnailHeight,
            attachment.sizeBytes,
            attachment.isSpoiler,
            attachment.durationMs,
        ),
    )
}

internal fun decodeGalleryMediaSeed(
    encoded: String?,
    expectedKey: ThreadKey,
    attachmentId: String?,
): MediaAttachment? {
    if (encoded == null || attachmentId.isNullOrBlank()) return null
    return runCatching { Json.decodeFromString<GalleryMediaSeed>(encoded) }
        .getOrNull()
        ?.takeIf { it.matchesSelection(expectedKey, attachmentId) }
        ?.toAttachment()
        ?.takeIf { it.canSeedGallery() }
}

private fun validSeedOwner(
    provider: String,
    board: String,
    thread: Long,
): Boolean = provider.isNotBlank() && board.isNotBlank() && thread > 0

private fun GalleryMediaSeed.matchesSelection(
    expectedKey: ThreadKey,
    attachmentId: String,
): Boolean = validSeedOwner(provider, board, thread) && matchesThread(expectedKey) && id == attachmentId

private fun GalleryMediaSeed.matchesThread(expectedKey: ThreadKey): Boolean =
    provider == expectedKey.provider.value && board == expectedKey.board.value && thread == expectedKey.thread.value

private fun GalleryMediaSeed.toAttachment(): MediaAttachment? {
    val mediaType = runCatching { MediaType.valueOf(type) }.getOrNull() ?: return null
    return MediaAttachment(
        id = id,
        originalFileName = originalFileName,
        extension = extension,
        type = mediaType,
        sourceUrl = sourceUrl,
        thumbnailUrl = thumbnailUrl,
        width = width,
        height = height,
        thumbnailWidth = thumbnailWidth,
        thumbnailHeight = thumbnailHeight,
        sizeBytes = sizeBytes,
        isSpoiler = isSpoiler,
        durationMs = durationMs,
    )
}

private fun MediaAttachment.canSeedGallery(): Boolean {
    if (id.isBlank() || type == MediaType.UNKNOWN || isPermanentlyFiltered()) return false
    val uri = runCatching { URI(sourceUrl) }.getOrNull() ?: return false
    return (uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) &&
        !uri.host.isNullOrBlank()
}
