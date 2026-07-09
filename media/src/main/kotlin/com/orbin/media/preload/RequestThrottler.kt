package com.orbin.media.preload

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore

/**
 * Throttles concurrent and per-minute preload requests to prevent CDN rate limiting.
 * Coordinates concurrent request limits, rolling-window request limits, and cooldown backoff when
 * the remote server explicitly reports rate limiting.
 */
class RequestThrottler(
    maxConcurrent: Int = 1,
    private val delayBetweenRequests: Long = 250,
    private val maxPerMinute: Int = 0,
    private val defaultRateLimitCooldownMillis: Long = DEFAULT_RATE_LIMIT_COOLDOWN_MILLIS,
) {
    private val semaphore = Semaphore(maxConcurrent)
    private val requestTimestamps = ArrayDeque<Long>()
    private val lock = Any()
    private var cooldownUntilMillis: Long = 0L

    suspend fun acquire() {
        semaphore.acquire()
        throttleCooldown()
        throttlePerMinute()
        delay(delayBetweenRequests)
    }

    fun release() {
        semaphore.release()
    }

    fun recordResponse(
        statusCode: Int,
        retryAfter: String?,
    ) {
        if (statusCode != HTTP_TOO_MANY_REQUESTS && statusCode != HTTP_SERVICE_UNAVAILABLE) return
        val cooldownMillis = retryAfter?.toRetryAfterMillis() ?: defaultRateLimitCooldownMillis
        val until = System.currentTimeMillis() + cooldownMillis.coerceAtLeast(MIN_RATE_LIMIT_COOLDOWN_MILLIS)
        synchronized(lock) {
            cooldownUntilMillis = maxOf(cooldownUntilMillis, until)
        }
    }

    private suspend fun throttleCooldown() {
        val waitTime =
            synchronized(lock) {
                (cooldownUntilMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            }
        if (waitTime > 0) delay(waitTime)
    }

    private suspend fun throttlePerMinute() {
        if (maxPerMinute <= 0) return

        var waitTime = 0L
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val oneMinuteAgo = now - ONE_MINUTE_MILLIS

            requestTimestamps.removeAll { it < oneMinuteAgo }

            if (requestTimestamps.size >= maxPerMinute) {
                val oldestTimestamp = requestTimestamps.first()
                waitTime = (oldestTimestamp + ONE_MINUTE_MILLIS) - now
            }
        }

        if (waitTime > 0) delay(waitTime)

        synchronized(lock) {
            val now = System.currentTimeMillis()
            requestTimestamps.removeAll { it < now - ONE_MINUTE_MILLIS }
            requestTimestamps.addLast(now)
        }
    }

    private fun String.toRetryAfterMillis(): Long? =
        toLongOrNull()?.let { seconds -> seconds.coerceAtLeast(0L) * MILLIS_PER_SECOND }

    private companion object {
        const val ONE_MINUTE_MILLIS = 60_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVICE_UNAVAILABLE = 503
        const val MIN_RATE_LIMIT_COOLDOWN_MILLIS = 5_000L
        const val DEFAULT_RATE_LIMIT_COOLDOWN_MILLIS = 30_000L
    }
}
