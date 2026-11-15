package com.orbin.core.model

import kotlinx.serialization.Serializable

/**
 * Whether card and grid views may load the original still image instead of the thumbnail.
 *
 * The default, [WIFI_ONLY], needs an unmetered connection. Android Data Saver and iOS Low Data
 * Mode make [WIFI_ONLY] behave like [NEVER].
 */
@Serializable
enum class SharpPreviewsMode(
    val label: String,
) {
    ALWAYS("Always"),
    WIFI_ONLY("Wi-Fi only"),
    NEVER("Never"),
}

/**
 * Largest original still image, in bytes, that a card or grid preview may load.
 *
 * 4 MiB is large enough for a typical still picture and small enough that scrolling a feed of
 * cards does not pull tens of megabytes on a metered or unmetered connection. Videos, GIFs, and
 * attachments whose size is unknown always keep the thumbnail, regardless of this cap.
 */
const val SHARP_PREVIEW_MAX_BYTES = 4L * 1_024 * 1_024

/**
 * Whether a card or grid preview may load the original still image.
 *
 * All of these must be true: the catalog mode wants a sharp thumbnail, [setting] allows it on
 * the current network, the attachment is a still image, and its known size is at most
 * [maxSizeBytes]. A missing or non-positive [sizeBytes] is treated as unknown and stays on the
 * thumbnail. When the metered check cannot decide, callers should pass [isMetered] as true.
 */
fun shouldLoadSharpPreview(
    modeWantsSharp: Boolean,
    setting: SharpPreviewsMode,
    isMetered: Boolean,
    isDataSaver: Boolean,
    mediaType: MediaType,
    sizeBytes: Long,
    maxSizeBytes: Long = SHARP_PREVIEW_MAX_BYTES,
): Boolean {
    if (!modeWantsSharp) return false
    if (mediaType != MediaType.IMAGE) return false
    if (sizeBytes <= 0L || sizeBytes > maxSizeBytes) return false
    return setting.allowsOnNetwork(isMetered, isDataSaver)
}

private fun SharpPreviewsMode.allowsOnNetwork(
    isMetered: Boolean,
    isDataSaver: Boolean,
): Boolean =
    when (this) {
        SharpPreviewsMode.ALWAYS -> true
        SharpPreviewsMode.NEVER -> false
        SharpPreviewsMode.WIFI_ONLY -> !isMetered && !isDataSaver
    }
