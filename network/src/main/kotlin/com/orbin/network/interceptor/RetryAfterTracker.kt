package com.orbin.network.interceptor

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks CDN rate-limit windows. Interceptors call [record] when they see a 429 with a
 * Retry-After header; callers check [blockedUntilMs] before issuing requests so they can
 * surface a countdown to the user instead of hammering the CDN.
 */
object RetryAfterTracker {
    private val blockedHosts = ConcurrentHashMap<String, Long>()

    /** Records that [host] is rate-limited for [retryAfterSeconds] seconds from now. */
    fun record(
        host: String,
        retryAfterSeconds: Long,
    ) {
        val unblockAt = System.currentTimeMillis() + retryAfterSeconds * 1_000L
        blockedHosts[host] = unblockAt
    }

    /**
     * Returns the epoch-ms at which the block on [host] expires, or null if the host is not
     * currently blocked (or the block has already expired).
     */
    fun blockedUntilMs(host: String): Long? {
        val until = blockedHosts[host] ?: return null
        if (System.currentTimeMillis() >= until) {
            blockedHosts.remove(host)
            return null
        }
        return until
    }
}
