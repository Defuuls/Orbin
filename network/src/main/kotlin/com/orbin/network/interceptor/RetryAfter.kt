package com.orbin.network.interceptor

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** HTTP 429 vocabulary shared by every place that reacts to a CDN rate limit. */
object RetryAfter {
    const val HTTP_TOO_MANY_REQUESTS = 429

    /** Wait applied when the server refuses a request without saying how long to back off. */
    const val DEFAULT_SECONDS = 15L

    /**
     * Seconds to wait according to a `Retry-After` header: either delta-seconds or an HTTP-date.
     * Returns null when the header is missing or unparseable so callers can apply [DEFAULT_SECONDS];
     * a date in the past or a negative number is 0.
     */
    fun parseSeconds(
        header: String?,
        nowMs: Long = System.currentTimeMillis(),
    ): Long? {
        val value = header?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        value.toLongOrNull()?.let { return it.coerceAtLeast(0L) }
        val retryAtMs =
            runCatching {
                ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            }.getOrNull() ?: return null
        return ((retryAtMs - nowMs) / MILLIS_PER_SECOND).coerceAtLeast(0L)
    }

    private const val MILLIS_PER_SECOND = 1_000L
}
