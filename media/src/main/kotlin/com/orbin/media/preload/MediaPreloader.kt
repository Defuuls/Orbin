package com.orbin.media.preload

import android.app.ActivityManager
import android.content.Context
import co.touchlab.kermit.Logger
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
import com.orbin.network.interceptor.RetryAfterTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Preloads thumbnails and images while Orbin is foregrounded, with bounded concurrency and rate limiting.
 * Videos are deliberately not touched: a `HEAD` stores nothing, so the player fetches (and the encrypted
 * media cache keeps) the bytes itself.
 */
class MediaPreloader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val imageLoader: ImageLoader,
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
                                // Preloading is best effort: leave a CDN alone while a player has it blocked.
                                if (target.url.isCdnBlocked()) continue
                                plan.throttler.acquire()
                                try {
                                    preloadImage(target.url)
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
                        // Pixel XL multi-column feeds amplify concurrency; keep aggressive below
                        // the old 3/160 defaults so decode + network stay bounded.
                        throttler = RequestThrottler(maxConcurrent = 2, delayBetweenRequests = 150, maxPerMinute = 90),
                        workerCount = 2,
                        maxTargets = 96,
                    )
                PreloadThrottleMode.UNLIMITED ->
                    PreloadPlan(
                        throttler = RequestThrottler(maxConcurrent = 3, delayBetweenRequests = 100, maxPerMinute = 120),
                        workerCount = 3,
                        maxTargets = 128,
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
                        Logger.w(tag = TAG) { "Failed to preload image: ${url.redactForLog()}" }
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    Logger.w(tag = TAG) { "Failed to preload image: ${url.redactForLog()}" }
                }
        }

        private fun List<MediaAttachment>.preloadTargets(option: PreloadOption): List<PreloadTarget> =
            flatMap { attachment ->
                buildList {
                    val shouldPreloadThumbnail = option.includesThumbnails()
                    val shouldPreloadImage =
                        option.includesImages() &&
                            (attachment.type == MediaType.IMAGE || attachment.type == MediaType.ANIMATED_IMAGE)

                    if (shouldPreloadThumbnail && attachment.thumbnailUrl.isNotBlank()) {
                        add(
                            PreloadTarget(attachment.thumbnailUrl, attachment.originalFileName),
                        )
                    }
                    if (shouldPreloadImage) {
                        add(PreloadTarget(attachment.sourceUrl, attachment.originalFileName))
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
        )

        private companion object {
            const val TAG = "OrbinMediaPreloader"
        }
    }

private fun String.isCdnBlocked(): Boolean {
    val address = toHttpUrlOrNull() ?: return false
    return RetryAfterTracker.blockedUntilMs(address.host, address.toString()) != null
}

/** Host-only form for logs so release builds do not retain full media paths. */
private fun String.redactForLog(): String =
    runCatching {
        val host =
            android.net.Uri
                .parse(this)
                .host ?: return "[media]"
        "$host/…"
    }.getOrDefault("[media]")
