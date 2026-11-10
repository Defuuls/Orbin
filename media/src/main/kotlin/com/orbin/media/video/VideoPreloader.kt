package com.orbin.media.video

import com.orbin.network.di.VideoOkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Warms at most 1 MiB of the next clip without opening a decoder or blocking the visible page. */
@Singleton
class VideoPreloader
    @Inject
    constructor(
        @VideoOkHttp private val client: OkHttpClient,
        private val cache: VideoPrefixCache,
    ) {
        fun prefetch(
            url: String,
            scope: CoroutineScope,
        ): Job? {
            val generation = cache.beginPrefetch()
            val address = url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return null
            if (cache.get(url) != null) return null
            return scope.launch {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val call =
                        client.newCall(
                            Request
                                .Builder()
                                .url(address)
                                .header("Range", "bytes=0-${VideoPrefixCache.MAX_PREFIX_BYTES - 1}")
                                .header("Accept-Encoding", "identity")
                                .build(),
                        )
                    continuation.invokeOnCancellation {
                        cache.cancelPrefetch(generation)
                        call.cancel()
                    }
                    call.enqueue(
                        object : Callback {
                            override fun onFailure(
                                call: Call,
                                e: IOException,
                            ) {
                                // Speculation must never turn a playback into an error or a retry loop.
                                if (continuation.isActive) continuation.resume(Unit)
                            }

                            override fun onResponse(
                                call: Call,
                                response: Response,
                            ) {
                                try {
                                    response.use { cacheResponse(url, generation, it) { continuation.isActive } }
                                } catch (_: IOException) {
                                    // Offline, cancellation and CDN throttling simply leave a cold cache.
                                } finally {
                                    if (continuation.isActive) continuation.resume(Unit)
                                }
                            }
                        },
                    )
                }
            }
        }

        private fun cacheResponse(
            url: String,
            generation: Long,
            response: Response,
            active: () -> Boolean,
        ) {
            val declaredTotal = cacheableTotalLength(response) ?: return
            val contentLength = response.body.contentLength()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(CHUNK_BYTES)
            val input = response.body.byteStream()
            var reachedEnd = false
            while (active() && output.size() < VideoPrefixCache.MAX_PREFIX_BYTES) {
                val count = input.read(buffer, 0, minOf(buffer.size, VideoPrefixCache.MAX_PREFIX_BYTES - output.size()))
                if (count < 0) {
                    reachedEnd = true
                    break
                }
                output.write(buffer, 0, count)
            }
            if (active()) {
                val bytes = output.toByteArray()
                val completeBody = reachedEnd || contentLength == bytes.size.toLong()
                if (response.code == HTTP_OK && !completeBody) return
                val total =
                    if (declaredTotal >= 0L) {
                        declaredTotal
                    } else if (reachedEnd) {
                        bytes.size.toLong()
                    } else {
                        -1L
                    }
                if (total >= 0L && bytes.size > total) return
                cache.put(url, bytes, total, response.headers.toMultimap(), expectedGeneration = generation)
            }
        }

        @Suppress("ReturnCount") // Reject invalid speculative responses before retaining media bytes.
        private fun cacheableTotalLength(response: Response): Long? {
            if (response.code !in listOf(HTTP_OK, HTTP_PARTIAL_CONTENT)) return null
            val contentLength = response.body.contentLength()
            // An origin ignoring Range cannot reuse a prefix without downloading/skipping it again.
            if (response.code == HTTP_OK && contentLength > VideoPrefixCache.MAX_PREFIX_BYTES) return null
            val type = response.body.contentType()
            if (type?.type !in listOf("video", "audio") && type?.subtype !in listOf("octet-stream", "ogg")) return null
            val range = response.header("Content-Range")?.let { CONTENT_RANGE.matchEntire(it) }
            if (response.code == HTTP_PARTIAL_CONTENT &&
                (range == null || range.groupValues[CONTENT_RANGE_START_GROUP] != "0")
            ) {
                return null
            }
            if (range != null) {
                val end = range.groupValues[CONTENT_RANGE_END_GROUP].toLongOrNull() ?: return null
                val total = range.groupValues[CONTENT_RANGE_TOTAL_GROUP].toLongOrNull() ?: return null
                if (end < 0L || end >= total) return null
            }
            return if (response.code == HTTP_PARTIAL_CONTENT) {
                range?.groupValues?.get(CONTENT_RANGE_TOTAL_GROUP)?.toLongOrNull()
            } else {
                contentLength
            }
        }

        fun clear() = cache.clear()

        private companion object {
            const val HTTP_OK = 200
            const val HTTP_PARTIAL_CONTENT = 206
            const val CONTENT_RANGE_START_GROUP = 1
            const val CONTENT_RANGE_END_GROUP = 2
            const val CONTENT_RANGE_TOTAL_GROUP = 3
            const val CHUNK_BYTES = 16 * 1024
            val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
        }
    }
