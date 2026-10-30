package com.orbin.network.interceptor

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * OkHttp interceptor for video requests. On a 429 response it reads the `Retry-After` header
 * (falling back to [RetryAfter.DEFAULT_SECONDS]) and records the block in [RetryAfterTracker] so
 * the UI can surface a countdown without hitting the CDN again. Requests that do reach the CDN are
 * spaced out per URL by [pacer].
 */
class VideoRetryAfterInterceptor(
    private val pacer: VideoRequestPacer = VideoRequestPacer(),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val url = request.url.toString()

        blockedResponse(request)?.let { return it }

        // A sibling request may be refused while this one waits its turn, so check again before sending.
        var refusedLocally = false
        val response =
            pacer.paced(url, chain.call()::isCanceled) {
                blockedResponse(request)?.also { refusedLocally = true } ?: chain.proceed(request)
            }
        if (!refusedLocally && response.code == RetryAfter.HTTP_TOO_MANY_REQUESTS) {
            val retryAfter = RetryAfter.parseSeconds(response.header("Retry-After")) ?: RetryAfter.DEFAULT_SECONDS
            RetryAfterTracker.record(host, url, retryAfter)
        }
        return response
    }

    /** A synthetic 429 while the URL or its host is blocked, so ExoPlayer surfaces an error fast. */
    private fun blockedResponse(request: Request): Response? {
        val blockedUntil = RetryAfterTracker.blockedUntilMs(request.url.host, request.url.toString()) ?: return null
        val remainingSeconds = (blockedUntil - System.currentTimeMillis()) / 1_000L
        return Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(RetryAfter.HTTP_TOO_MANY_REQUESTS)
            .message("Too Many Requests (cached, retry in ${remainingSeconds}s)")
            .body(ByteArray(0).toResponseBody(null))
            .build()
    }
}
