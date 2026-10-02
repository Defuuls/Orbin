package com.orbin.network.policy

import com.orbin.network.NetworkConfig

/** Header changes to make to a request: [set] replaces any existing value, [remove] drops the header. */
data class HeaderEdits(
    val set: Map<String, String>,
    val remove: Set<String>,
)

/**
 * The rules every request is sent under, shared by Android (OkHttp interceptors in :network) and
 * iOS (the Ktor plugins in [com.orbin.network.ktor]). Read per request from the current
 * [NetworkConfig], so a settings change applies without rebuilding a client.
 */
object RequestPolicy {
    /** Whether a request with this URL [scheme] may leave the device. */
    fun allows(
        config: NetworkConfig,
        scheme: String,
    ): Boolean = !config.httpsOnly || scheme.equals("https", ignoreCase = true)

    /**
     * The header changes for a request.
     *
     * Static media is fetched like a browser would: a media Accept, the site's own origin as
     * Referer (some media hosts reject hotlink-looking requests), and normal HTTP caching.
     * Idempotent API GETs (catalogs, threads, board lists) may use a short-lived cache. Everything
     * else is no-store, so proof-of-work and cookie gates and POSTs are never served stale.
     *
     * @param originReferer the request's origin with a `/` path, e.g. `https://i.example.org/`.
     */
    fun headers(
        config: NetworkConfig,
        method: String,
        encodedPath: String,
        originReferer: String,
    ): HeaderEdits {
        val userAgent = USER_AGENT to config.userAgent
        return when {
            isStaticMedia(method, encodedPath) ->
                HeaderEdits(
                    set = mapOf(userAgent, ACCEPT to MEDIA_ACCEPT, REFERER to originReferer),
                    remove = setOf(CACHE_CONTROL, PRAGMA),
                )
            method == "GET" ->
                HeaderEdits(
                    set =
                        mapOf(
                            userAgent,
                            ACCEPT to API_ACCEPT,
                            CACHE_CONTROL to "max-age=$CACHEABLE_API_MAX_AGE_SECONDS",
                        ),
                    remove = setOf(PRAGMA),
                )
            else ->
                HeaderEdits(
                    set = mapOf(userAgent, ACCEPT to API_ACCEPT, CACHE_CONTROL to "no-store", PRAGMA to "no-cache"),
                    remove = emptySet(),
                )
        }
    }

    private fun isStaticMedia(
        method: String,
        encodedPath: String,
    ): Boolean {
        if (method != "GET") return false
        val path = encodedPath.lowercase()
        return MEDIA_EXTENSIONS.any { path.endsWith(it) }
    }

    private const val USER_AGENT = "User-Agent"
    private const val ACCEPT = "Accept"
    private const val REFERER = "Referer"
    private const val CACHE_CONTROL = "Cache-Control"
    private const val PRAGMA = "Pragma"
    private const val MEDIA_ACCEPT = "image/avif,image/webp,image/*,video/*,audio/*,*/*;q=0.8"
    private const val API_ACCEPT = "application/json, image/*, */*"
    private const val CACHEABLE_API_MAX_AGE_SECONDS = 60
    private val MEDIA_EXTENSIONS =
        setOf(
            ".jpg",
            ".jpeg",
            ".png",
            ".gif",
            ".webp",
            ".avif",
            ".bmp",
            ".webm",
            ".mp4",
            ".m4v",
            ".mov",
            ".mp3",
            ".ogg",
            ".opus",
            ".wav",
        )
}
