package com.orbin.media.preload

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Warms Coil's memory/disk caches for image media and thumbnails before the user opens them. */
class MediaPreloader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val imageLoader: ImageLoader,
    ) {
        suspend fun preload(
            attachments: List<MediaAttachment>,
            onProgress: (current: Int, total: Int, label: String) -> Unit,
        ): Int {
            val targets = attachments.preloadTargets()
            if (targets.isEmpty()) return 0

            targets.forEachIndexed { index, target ->
                onProgress(index + 1, targets.size, target.label)
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(target.url)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                runCatching { imageLoader.execute(request) }
                    .onSuccess { result ->
                        if (result is ErrorResult) {
                            Log.w(TAG, "Failed to preload media", result.throwable)
                        }
                    }.onFailure { error ->
                        Log.w(TAG, "Failed to preload media", error)
                    }
            }
            return targets.size
        }

        private fun List<MediaAttachment>.preloadTargets(): List<PreloadTarget> =
            flatMap { attachment ->
                buildList {
                    if (attachment.thumbnailUrl.isNotBlank()) {
                        add(PreloadTarget(attachment.thumbnailUrl, attachment.originalFileName))
                    }
                    if (attachment.type == MediaType.IMAGE || attachment.type == MediaType.ANIMATED_IMAGE) {
                        add(PreloadTarget(attachment.sourceUrl, attachment.originalFileName))
                    }
                }
            }.distinctBy { it.url }

        private data class PreloadTarget(
            val url: String,
            val label: String,
        )

        private companion object {
            const val TAG = "OrbinMediaPreloader"
        }
    }
