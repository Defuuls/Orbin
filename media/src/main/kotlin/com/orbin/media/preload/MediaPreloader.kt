package com.orbin.media.preload

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.PreloadOption
import com.orbin.core.model.PreloadThrottleMode
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Request
import javax.inject.Inject

/**
 * Preloads media (images, videos, thumbnails) into cache before user views them.
 * Supports granular control over preload types and rate limiting via throttling to avoid
 * CDN rate limits. Works with Coil for images and direct HEAD requests for videos.
 */
class MediaPreloader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val imageLoader: ImageLoader,
    ) {
        suspend fun preload(
            attachments: List<MediaAttachment>,
            option: PreloadOption = PreloadOption.IMAGES,
            throttleMode: PreloadThrottleMode = PreloadThrottleMode.MODERATE,
            onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
        ): Int {
            if (option == PreloadOption.NONE) return 0

            val throttler = createThrottler(throttleMode)
            val targets = attachments.preloadTargets(option)
            if (targets.isEmpty()) return 0

            targets.forEachIndexed { index, target ->
                onProgress(index + 1, targets.size, target.label)
                throttler.acquire()
                try {
                    when (target.type) {
                        PreloadTargetType.IMAGE -> preloadImage(target.url)
                        PreloadTargetType.VIDEO -> preloadVideo(target.url)
                    }
                } finally {
                    throttler.release()
                }
            }
            return targets.size
        }

        private fun createThrottler(mode: PreloadThrottleMode): RequestThrottler =
            when (mode) {
                PreloadThrottleMode.CONSERVATIVE ->
                    RequestThrottler(maxConcurrent = 1, delayBetweenRequests = 500, maxPerMinute = 30)
                PreloadThrottleMode.MODERATE ->
                    RequestThrottler(maxConcurrent = 2, delayBetweenRequests = 250, maxPerMinute = 60)
                PreloadThrottleMode.AGGRESSIVE ->
                    RequestThrottler(maxConcurrent = 3, delayBetweenRequests = 100, maxPerMinute = 120)
            }

        private suspend fun preloadImage(url: String) {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            runCatching { imageLoader.execute(request) }
                .onSuccess { result ->
                    if (result is ErrorResult) {
                        Log.w(TAG, "Failed to preload image: $url", result.throwable)
                    }
                }.onFailure { error ->
                    Log.w(TAG, "Failed to preload image: $url", error)
                }
        }

        private suspend fun preloadVideo(url: String) {
            // Preload video by fetching metadata via HEAD request or by warming up the cache
            // This validates the URL is accessible without downloading the full video
            runCatching {
                imageLoader.networkClient.newCall(
                    Request.Builder()
                        .head()
                        .url(url)
                        .build()
                ).execute().close()
            }.onSuccess {
                Log.d(TAG, "Preloaded video metadata: $url")
            }.onFailure { error ->
                Log.w(TAG, "Failed to preload video: $url", error)
            }
        }

        private fun List<MediaAttachment>.preloadTargets(option: PreloadOption): List<PreloadTarget> =
            flatMap { attachment ->
                buildList {
                    val shouldPreloadThumbnail = option.includesThumbnails()
                    val shouldPreloadImage =
                        option.includesImages() &&
                            (attachment.type == MediaType.IMAGE || attachment.type == MediaType.ANIMATED_IMAGE)
                    val shouldPreloadVideo =
                        option.includesVideos() && attachment.type == MediaType.VIDEO

                    if (shouldPreloadThumbnail && attachment.thumbnailUrl.isNotBlank()) {
                        add(
                            PreloadTarget(
                                attachment.thumbnailUrl,
                                attachment.originalFileName,
                                PreloadTargetType.IMAGE,
                            )
                        )
                    }
                    if (shouldPreloadImage) {
                        add(
                            PreloadTarget(
                                attachment.sourceUrl,
                                attachment.originalFileName,
                                PreloadTargetType.IMAGE,
                            )
                        )
                    }
                    if (shouldPreloadVideo) {
                        add(
                            PreloadTarget(
                                attachment.sourceUrl,
                                attachment.originalFileName,
                                PreloadTargetType.VIDEO,
                            )
                        )
                    }
                }
            }.distinctBy { it.url }

        private data class PreloadTarget(
            val url: String,
            val label: String,
            val type: PreloadTargetType,
        )

        private enum class PreloadTargetType {
            IMAGE,
            VIDEO,
        }

        private companion object {
            const val TAG = "OrbinMediaPreloader"
        }
    }
