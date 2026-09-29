package com.orbin.network.interceptor

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks CDN rate-limit windows. Interceptors call [record] when they see a 429; callers check
 * [blockedUntilMs] before issuing requests so they can surface a countdown instead of hammering
 * the CDN.
 *
 * A 429 blocks only the URL that received it. The whole host is blocked only once several
 * distinct URLs on it are refused within [HOST_ESCALATION_WINDOW_MS]. Blocking the host on the
 * first 429 meant a single refused clip (common right after the app returns from the background
 * and every visible player prepares at once) took down every video for minutes.
 * Every window is capped at [MAX_BLOCK_SECONDS] so a huge or bogus Retry-After can't strand playback.
 */
object RetryAfterTracker {
    private const val MAX_BLOCK_SECONDS = 60L
    private const val HOST_ESCALATION_URLS = 3
    private const val HOST_ESCALATION_WINDOW_MS = 30_000L

    private val blockedUrls = ConcurrentHashMap<String, Long>()
    private val blockedHosts = ConcurrentHashMap<String, Long>()
    private val recentHits = ConcurrentHashMap<String, MutableMap<String, Long>>()

    /** Records that [url] on [host] was rate-limited for [retryAfterSeconds] seconds from now. */
    fun record(
        host: String,
        url: String,
        retryAfterSeconds: Long,
    ) {
        val now = System.currentTimeMillis()
        val unblockAt = now + retryAfterSeconds.coerceIn(0L, MAX_BLOCK_SECONDS) * 1_000L
        blockedUrls[url] = unblockAt
        val hits = recentHits.computeIfAbsent(host) { ConcurrentHashMap() }
        hits.entries.removeIf { now - it.value > HOST_ESCALATION_WINDOW_MS }
        hits[url] = now
        if (hits.size >= HOST_ESCALATION_URLS) {
            blockedHosts[host] = unblockAt
            hits.clear()
        }
    }

    /**
     * Returns the epoch-ms at which the block covering [url] on [host] expires, or null if neither
     * the URL nor its host is currently blocked.
     */
    fun blockedUntilMs(
        host: String,
        url: String,
    ): Long? {
        val now = System.currentTimeMillis()
        val urlUntil = blockedUrls.activeUntil(url, now)
        val hostUntil = blockedHosts.activeUntil(host, now)
        return listOfNotNull(urlUntil, hostUntil).maxOrNull()
    }

    /** Forgets every block, e.g. when the reader explicitly retries. */
    fun clear() {
        blockedUrls.clear()
        blockedHosts.clear()
        recentHits.clear()
    }

    private fun ConcurrentHashMap<String, Long>.activeUntil(
        key: String,
        now: Long,
    ): Long? {
        val until = this[key] ?: return null
        if (now >= until) {
            remove(key, until)
            return null
        }
        return until
    }
}
