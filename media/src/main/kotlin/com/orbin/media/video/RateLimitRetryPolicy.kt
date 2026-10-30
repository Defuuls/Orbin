package com.orbin.media.video

import kotlin.random.Random

/**
 * When a video that the CDN refused with a 429 may load itself again. Waits at least the server's
 * `Retry-After` and never less than [floorMs]. Jitter keeps players that were refused together from
 * retrying together, and after [maxAutoRetries] it stops so a CDN that keeps refusing is not
 * hammered; the reader can still retry by hand.
 */
internal class RateLimitRetryPolicy(
    private val maxAutoRetries: Int = MAX_AUTO_RETRIES,
    private val floorMs: Long = FLOOR_MS,
    private val maxJitterMs: Long = MAX_JITTER_MS,
    private val jitter: (Long) -> Long = { bound -> Random.nextLong(bound + 1) },
) {
    /** Delay before the next automatic retry, or null once [autoRetriesSoFar] has reached the cap. */
    fun delayMs(
        autoRetriesSoFar: Int,
        retryAfterSeconds: Long,
    ): Long? {
        if (autoRetriesSoFar >= maxAutoRetries) return null
        val serverMs = retryAfterSeconds.coerceAtLeast(0L) * MILLIS_PER_SECOND
        return maxOf(serverMs, floorMs) + jitter(maxJitterMs)
    }

    private companion object {
        const val MAX_AUTO_RETRIES = 3
        const val FLOOR_MS = 5_000L
        const val MAX_JITTER_MS = 3_000L
    }
}
