package com.orbin.network.interceptor

import com.orbin.network.MediaResponseCache
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Share encoded media and exact video ranges across decoder/player instances without a plaintext HTTP/Coil cache. */
class MediaResponseCacheInterceptor(
    private val cache: MediaResponseCache,
    private val cookies: CookieJar = CookieJar.NO_COOKIES,
    private val now: () -> Long = System::currentTimeMillis,
) : Interceptor {
    private class Flight {
        val permit = Semaphore(1)
        var references = 0
    }

    private val flights = mutableMapOf<String, Flight>()

    @Suppress("NestedBlockDepth", "ReturnCount") // Explicit pipeline exits share one finally/release boundary.
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET" || request.header("Authorization") != null || request.cacheControl.noStore) {
            return chain.proceed(request)
        }
        val key = cacheKey(request)
        val release = acquire(key, chain)
        var releaseWithBody = false
        try {
            if (!request.cacheControl.noCache) {
                val entry = bestEffort { cache.open(key, now()) }
                if (entry != null) {
                    val maxAge = request.cacheControl.maxAgeSeconds
                    if (maxAge < 0 || now() - entry.storedAtMillis <= maxAge * MILLIS_PER_SECOND) {
                        return cachedResponse(request, entry)
                    }
                    entry.source.close()
                }
            }
            if (request.cacheControl.onlyIfCached) {
                return Response
                    .Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(HTTP_CACHE_MISS)
                    .message("Unsatisfiable Request (only-if-cached)")
                    .body(ByteArray(0).toResponseBody(null))
                    .build()
            }
            val response = chain.proceed(request)
            if (!canStore(response)) return response
            val control = response.cacheControl
            val lifetime = if (control.maxAgeSeconds >= 0) control.maxAgeSeconds.toLong() else DEFAULT_AGE_SECONDS
            val age = response.header("Age")?.toLongOrNull()?.coerceAtLeast(0) ?: 0L
            val remaining = (lifetime.coerceAtMost(MAX_AGE_SECONDS) - age).coerceAtLeast(0)
            val writer =
                bestEffort {
                    cache.start(
                        key,
                        response.body.contentType().toString(),
                        now() + remaining * MILLIS_PER_SECOND,
                        response.code,
                        response.header("Content-Range"),
                    )
                } ?: return response
            return capture(response, writer, release).also { releaseWithBody = true }
        } finally {
            if (!releaseWithBody) release()
        }
    }

    private fun cacheKey(request: Request): String =
        buildString {
            append(request.url).append('\n')
            request.headers.names().filterNot { it.lowercase() in TRANSIENT_HEADERS }.sorted().forEach { name ->
                append(name.lowercase()).append(':').append(request.headers.values(name).joinToString()).append('\n')
            }
            append(cookies.loadForRequest(request.url).joinToString(";"))
        }

    private fun canStore(response: Response): Boolean =
        response.code in CACHEABLE_CODES &&
            response.body.contentType()?.type in MEDIA_TYPES &&
            !response.cacheControl.noStore &&
            !response.cacheControl.noCache &&
            response.body.contentLength() != 0L &&
            response.body.contentLength() <= MAX_ENTRY_BYTES &&
            response.headers("Set-Cookie").isEmpty() &&
            validRange(response) &&
            response.header("Vary")?.split(',')?.none {
                it.trim() == "*" || it.trim().lowercase() in TRANSIENT_HEADERS
            } != false

    private fun validRange(response: Response): Boolean {
        if (response.code != HTTP_PARTIAL) return true
        val match = CONTENT_RANGE.matchEntire(response.header("Content-Range").orEmpty()) ?: return false
        val values = match.groupValues.drop(1).mapNotNull(String::toLongOrNull)
        if (values.size != RANGE_VALUES) return false
        val (start, end, total) = values
        val ordered = start <= end && end < total
        val length = response.body.contentLength()
        return ordered && (length < 0 || end - start + 1 == length)
    }

    private fun cachedResponse(
        request: Request,
        entry: MediaResponseCache.Entry,
    ): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(entry.statusCode)
            .message(if (entry.statusCode == HTTP_PARTIAL) "Partial Content" else "OK")
            .apply { entry.contentRange?.let { header("Content-Range", it) } }
            .body(
                object : ResponseBody() {
                    private val input = entry.source.buffer()

                    override fun contentType() = entry.contentType.toMediaTypeOrNull()

                    override fun contentLength() = entry.byteCount

                    override fun source() = input
                },
            ).build()

    @Suppress("NestedBlockDepth") // Streaming source callbacks must release the same request flight.
    private fun capture(
        response: Response,
        initial: MediaResponseCache.Write,
        release: () -> Unit,
    ): Response {
        val body = response.body
        var writer: MediaResponseCache.Write? = initial
        var received = 0L
        val input =
            object : ForwardingSource(body.source()) {
                private fun abort() {
                    writer?.let { bestEffort { it.abort() } }
                    writer = null
                }

                override fun read(
                    sink: Buffer,
                    byteCount: Long,
                ): Long {
                    try {
                        val count = super.read(sink, minOf(byteCount, CHUNK_BYTES))
                        if (count > 0) {
                            received += count
                            writer?.let { active ->
                                val copy = Buffer()
                                sink.copyTo(copy, sink.size - count, count)
                                if (bestEffort { active.append(copy.readByteArray()) } == null) abort()
                            }
                        }
                        if (count == -1L || received == body.contentLength()) {
                            writer?.let { active ->
                                val complete = body.contentLength() < 0 || received == body.contentLength()
                                if (!complete || bestEffort { active.commit() } == null) bestEffort { active.abort() }
                            }
                            writer = null
                            release()
                        }
                        return count
                    } catch (failure: IOException) {
                        abort()
                        release()
                        throw failure
                    }
                }

                override fun close() {
                    try {
                        abort()
                        super.close()
                    } finally {
                        release()
                    }
                }
            }.buffer()
        return response
            .newBuilder()
            .body(
                object : ResponseBody() {
                    override fun contentType() = body.contentType()

                    override fun contentLength() = body.contentLength()

                    override fun source() = input
                },
            ).build()
    }

    private fun acquire(
        key: String,
        chain: Interceptor.Chain,
    ): () -> Unit {
        val flight = synchronized(flights) { flights.getOrPut(key) { Flight() }.also { it.references++ } }
        var acquired = false
        try {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(FLIGHT_WAIT_MILLIS)
            while (!flight.permit.tryAcquire(FLIGHT_POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                if (chain.call().isCanceled()) throw IOException("Canceled")
                // A paused player can retain its response body; it must not stall another player.
                if (System.nanoTime() >= deadline) return {}
            }
            acquired = true
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted waiting for media", interrupted)
        } finally {
            if (!acquired) dropReference(key, flight)
        }
        val released = AtomicBoolean(false)
        return {
            if (released.compareAndSet(false, true)) {
                flight.permit.release()
                dropReference(key, flight)
            }
        }
    }

    private fun dropReference(
        key: String,
        flight: Flight,
    ) = synchronized(flights) {
        flight.references--
        if (flight.references == 0) flights.remove(key)
        Unit
    }

    // Cache/database availability must not make a usable origin response fail.
    private inline fun <T> bestEffort(block: () -> T): T? = runCatching(block).getOrNull()

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_PARTIAL = 206
        const val RANGE_VALUES = 3
        val CACHEABLE_CODES = setOf(HTTP_OK, HTTP_PARTIAL)
        val MEDIA_TYPES = setOf("image", "video", "audio")
        val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
        const val HTTP_CACHE_MISS = 504
        const val FLIGHT_WAIT_MILLIS = 2000L
        const val FLIGHT_POLL_MILLIS = 100L
        const val CHUNK_BYTES = 64L * 1024L
        const val MAX_ENTRY_BYTES = 32L * 1024L * 1024L
        const val DEFAULT_AGE_SECONDS = 300L
        const val MAX_AGE_SECONDS = 86400L
        const val MILLIS_PER_SECOND = 1000L
        val TRANSIENT_HEADERS = setOf("cache-control", "pragma", "connection")
    }
}
