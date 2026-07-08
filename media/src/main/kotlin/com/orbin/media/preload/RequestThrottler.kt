package com.orbin.media.preload

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore

/**
 * Throttles concurrent and per-minute preload requests to prevent CDN rate limiting.
 * Coordinates both concurrent request limits and rate limits over a rolling minute.
 */
class RequestThrottler(
    private val maxConcurrent: Int = 1,
    private val delayBetweenRequests: Long = 250,
    private val maxPerMinute: Int = 0,
) {
    private val semaphore = Semaphore(maxConcurrent)
    private val requestTimestamps = ArrayDeque<Long>()
    private val lock = Any()

    companion object {
        private const val ONE_MINUTE_MILLIS = 60_000L
    }

    suspend fun acquire() {
        semaphore.acquire()
        throttlePerMinute()
        delay(delayBetweenRequests)
    }

    fun release() {
        semaphore.release()
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

        if (waitTime > 0) {
            delay(waitTime)
        }

        synchronized(lock) {
            val now = System.currentTimeMillis()
            requestTimestamps.removeAll { it < now - ONE_MINUTE_MILLIS }
            requestTimestamps.addLast(now)
        }
    }
}
