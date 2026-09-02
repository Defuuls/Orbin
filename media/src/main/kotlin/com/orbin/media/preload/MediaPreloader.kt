package com.orbin.media.preload

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.PreloadOption
import com.orbin.core.model.PreloadThrottleMode
import com.orbin.network.di.BaseOkHttp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/** Preloads media while Orbin is foregrounded, with bounded concurrency and rate limiting. */
class MediaPreloader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val imageLoader: ImageLoader,
        @BaseOkHttp private val okHttpClient: OkHttpClient,
        @Dispatcher(OrbinDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend fun preload(
            attachments: List<MediaAttachment>,
            option: PreloadOption = PreloadOption.IMAGES,
            throttleMode: PreloadThrottleMode = PreloadThrottleMode.MODERATE,
            onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
        ): Int {
            if (option == PreloadOption.NONE || !isAppForeground()) return 0

            val plan = createPreloadPlan(throttleMode)
            val targets = attachments.preloadTargets(option).take(plan.maxTargets)
            if (targets.isEmpty()) return 0

            val completed = AtomicInteger(0)
            withContext(ioDispatcher) {
                coroutineScope {
                    val queue = Channel<PreloadTarget>(capacity = plan.workerCount)
                    repeat(plan.workerCount) {
                        launch {
                            for (target in queue) {
                                if (!isAppForeground()) continue
                                plan.throttler.acquire()
                                try {
                                    when (target.type) {
                                        PreloadTargetType.IMAGE -> preloadImage(target.url)
                                        PreloadTargetType.VIDEO -> preloadVideo(target.url, plan.throttler)
                                    }
                                } finally {
                                    plan.throttler.release()
                                }
                                val current = completed.incrementAndGet()
                                onProgress(current, targets.size, target.label)
                            }
                        }
                    }
                    targets.forEach { queue.send(it) }
                    queue.close()
                }
            }
            return completed.get()
        }

        private fun isAppForeground(): Boolean {
            val state = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(state)
            return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        }

        private fun createPreloadPlan(mode: PreloadThrottleMode): PreloadPlan =
            when (mode) {
                PreloadThrottleMode.CONSERVATIVE ->
                    PreloadPlan(
                        throttler = RequestThrottler(maxConcurrent = 1, delayBetweenRequests = 500, maxPerMinute = 30),
                        workerCount = 1,
                        maxTargets = 48,
                    )
                PreloadThrottleMode.MODERATE ->
                    PreloadPlan(
                        throttler = RequestThrottler(maxConcurrent = 2, delayBetweenRequests = 250, maxPerMinute = 60),
                        workerCount = 2,
                        maxTargets = 96,
                    )
                PreloadThrottleMode.AGGRESSIVE ->
                    PreloadPlan(
                        throttler = RequestThrottler(maxConcurrent = 3, delayBetweenRequests = 100, maxPerMinute = 120),
                        workerCount = 3,
                        maxTargets = 160,
                    )
                PreloadThrottleMode.UNLIMITED ->
                    PreloadPlan(
                        throttler = RequestThrottler(maxConcurrent = 4, delayBetweenRequests = 75, maxPerMinute = 180),
                        workerCount = 4,
                        maxTargets = 240,
                    )
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

        private suspend fun preloadVideo(
            url: String,
            throttler: RequestThrottler,
        ) {
            runCatching {
                okHttpClient
                    .newCall(
                        Request
                            .Builder()
                            .head()
                            .url(url)
                            .build(),
                    ).execute()
                    .use { response ->
                        throttler.recordResponse(response.code, response.header(RETRY_AFTER_HEADER))
                    }
            }.onSuccess { _ ->
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
                    val shouldPreloadVideo = option.includesVideos() && attachment.type == MediaType.VIDEO

                    if (shouldPreloadThumbnail && attachment.thumbnailUrl.isNotBlank()) {
                        add(
                            PreloadTarget(
                                attachment.thumbnailUrl,
                                attachment.originalFileName,
                                PreloadTargetType.IMAGE,
                            ),
                        )
                    }
                    if (shouldPreloadImage) {
                        add(PreloadTarget(attachment.sourceUrl, attachment.originalFileName, PreloadTargetType.IMAGE))
                    }
                    if (shouldPreloadVideo) {
                        add(PreloadTarget(attachment.sourceUrl, attachment.originalFileName, PreloadTargetType.VIDEO))
                    }
                }
            }.distinctBy { it.url }

        private data class PreloadPlan(
            val throttler: RequestThrottler,
            val workerCount: Int,
            val maxTargets: Int,
        )

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
            const val RETRY_AFTER_HEADER = "Retry-After"
        }
    }
