package com.orbin.media.preload

import com.orbin.network.interceptor.RetryAfter
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
        var acquired = false
        try {
            throttleCooldown()
            throttlePerMinute()
            delay(delayBetweenRequests)
            acquired = true
        } finally {
            if (!acquired) {
                semaphore.release()
            }
        }
    }

    fun release() {
        semaphore.release()
    }

    fun recordResponse(
        statusCode: Int,
        retryAfter: String?,
    ) {
        if (statusCode != RetryAfter.HTTP_TOO_MANY_REQUESTS && statusCode != HTTP_SERVICE_UNAVAILABLE) return
        val cooldownMillis =
            RetryAfter.parseSeconds(retryAfter)?.times(MILLIS_PER_SECOND) ?: defaultRateLimitCooldownMillis
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

        while (true) {
            val waitTime =
                synchronized(lock) {
                    val now = System.currentTimeMillis()
                    val oneMinuteAgo = now - ONE_MINUTE_MILLIS

                    requestTimestamps.removeAll { it < oneMinuteAgo }

                    if (requestTimestamps.size < maxPerMinute) {
                        requestTimestamps.addLast(now)
                        return
                    }
                    val oldestTimestamp = requestTimestamps.first()
                    (oldestTimestamp + ONE_MINUTE_MILLIS - now).coerceAtLeast(1L)
                }
            delay(waitTime)
        }
    }

    private companion object {
        const val ONE_MINUTE_MILLIS = 60_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val HTTP_SERVICE_UNAVAILABLE = 503
        const val MIN_RATE_LIMIT_COOLDOWN_MILLIS = 5_000L
        const val DEFAULT_RATE_LIMIT_COOLDOWN_MILLIS = RetryAfter.DEFAULT_SECONDS * MILLIS_PER_SECOND
    }
}
