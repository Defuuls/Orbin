package com.orbin.ios

import com.orbin.core.model.MediaAttachment

/** Reserve preview space before decoding, including providers that only publish thumbnail dimensions. */
internal fun MediaAttachment.threadPreviewAspect(): Float =
    previewAspectRatio.takeIf { it.isFinite() && it > 0f }?.coerceIn(
        MIN_THREAD_PREVIEW_ASPECT,
        MAX_THREAD_PREVIEW_ASPECT,
    )
        ?: 1f

private const val MIN_THREAD_PREVIEW_ASPECT = 0.5625f
private const val MAX_THREAD_PREVIEW_ASPECT = 3f
