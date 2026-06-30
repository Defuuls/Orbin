package com.orbin.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Handles server-side throttling without trying to evade it. A single bounded retry is attempted
 * only when the origin gives an explicit Retry-After window that is short enough for foreground use.
 */
class RateLimitInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code !in RETRYABLE_STATUS_CODES) return response

        val delayMillis = response.retryAfterMillis() ?: return response
        if (delayMillis !in MIN_RETRY_DELAY_MS..MAX_RETRY_DELAY_MS) return response

        response.close()
        TimeUnit.MILLISECONDS.sleep(delayMillis)
        return chain.proceed(
            request
                .newBuilder()
                .header("X-Orbin-Retry", "rate-limit")
                .build(),
        )
    }

    private fun Response.retryAfterMillis(): Long? {
        val raw = header("Retry-After")?.trim().orEmpty()
        if (raw.isBlank()) return null
        return raw.toLongOrNull()?.let { seconds -> TimeUnit.SECONDS.toMillis(seconds) }
            ?: runCatching {
                val formatter = DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US)
                ZonedDateTime
                    .parse(raw, formatter)
                    .toInstant()
                    .toEpochMilli() - System.currentTimeMillis()
            }.getOrNull()
    }

    private companion object {
        const val MIN_RETRY_DELAY_MS = 1L
        const val MAX_RETRY_DELAY_MS = 30_000L
        val RETRYABLE_STATUS_CODES = setOf(429, 503)
    }
}
