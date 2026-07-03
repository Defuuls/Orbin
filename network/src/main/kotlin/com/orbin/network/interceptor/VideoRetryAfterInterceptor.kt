package com.orbin.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val DEFAULT_RETRY_AFTER_SECONDS = 300L

/**
 * OkHttp interceptor for video requests. On a 429 response it reads the `Retry-After` header
 * (falling back to [DEFAULT_RETRY_AFTER_SECONDS]) and records the block in [RetryAfterTracker] so
 * the UI can surface a countdown without hitting the CDN again.
 */
class VideoRetryAfterInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        val blockedUntil = RetryAfterTracker.blockedUntilMs(host)
        if (blockedUntil != null) {
            val remainingSeconds = (blockedUntil - System.currentTimeMillis()) / 1_000L
            // Return a synthetic 429 body immediately so ExoPlayer surfaces an error fast.
            return okhttp3.Response
                .Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(HTTP_TOO_MANY_REQUESTS)
                .message("Too Many Requests (cached, retry in ${remainingSeconds}s)")
                .body(ByteArray(0).toResponseBody(null))
                .build()
        }

        val response = chain.proceed(request)
        if (response.code == HTTP_TOO_MANY_REQUESTS) {
            val retryAfter =
                response.header("Retry-After")?.toLongOrNull()
                    ?: DEFAULT_RETRY_AFTER_SECONDS
            RetryAfterTracker.record(host, retryAfter)
        }
        return response
    }
}
