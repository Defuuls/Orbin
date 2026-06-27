package com.orbin.core.model

/** Broad classification of an attachment, used to pick the right renderer/player. */
enum class MediaType {
    IMAGE,
    ANIMATED_IMAGE, // gif / apng
    VIDEO,
    AUDIO,
    UNKNOWN,
}

/**
 * A single file attached to a post. Image boards typically attach one file per post, but the
 * model allows several so providers that support multi-file posts map cleanly.
 *
 * URLs are absolute and ready to load; resolving relative engine paths is the provider's job.
 */
data class MediaAttachment(
    val id: String,
    /** Original filename as uploaded, for display and downloads. */
    val originalFileName: String,
    val extension: String,
    val type: MediaType,
    /** Full-resolution media URL. */
    val sourceUrl: String,
    /** Thumbnail URL for grids and inline previews. */
    val thumbnailUrl: String,
    val width: Int = 0,
    val height: Int = 0,
    val thumbnailWidth: Int = 0,
    val thumbnailHeight: Int = 0,
    val sizeBytes: Long = 0,
    /** Marked as a spoiler by the poster; UI should blur until tapped. */
    val isSpoiler: Boolean = false,
    /** Duration in milliseconds for audio/video, when known. */
    val durationMs: Long? = null,
) {
    val isPlayable: Boolean get() = type == MediaType.VIDEO || type == MediaType.AUDIO

    /** Aspect ratio (width/height) guarding against divide-by-zero for layout placeholders. */
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f
}
