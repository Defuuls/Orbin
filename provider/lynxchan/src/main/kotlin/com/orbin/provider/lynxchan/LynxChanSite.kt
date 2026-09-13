package com.orbin.provider.lynxchan

import com.orbin.core.model.ProviderId
import java.net.URI

/**
 * Configuration for a single LynxChan site. Because every URL and flag is data, supporting
 * another instance is a matter of constructing a different [LynxChanSite] - no code changes.
 */
data class LynxChanSite(
    val providerId: ProviderId,
    val displayName: String,
    /** Site root, e.g. "https://example.org" (no trailing slash). Used for the API base, media
     * paths (which the engine returns site-relative), and "open in browser" links. */
    val siteUrl: String,
    /** Additional HTTPS hosts allowed for absolute media URLs returned by this provider. */
    val allowedMediaHosts: Set<String> = emptySet(),
    val nsfwByDefault: Boolean = false,
) {
    /** Retrofit needs a trailing-slash base URL. */
    val apiBaseUrl: String get() = "$siteUrl/"

    fun isAllowedMediaHost(host: String): Boolean {
        val siteHost = runCatching { URI(siteUrl).host }.getOrNull()
        return siteHost.equals(host, ignoreCase = true) ||
            allowedMediaHosts.any { it.equals(host, ignoreCase = true) }
    }

    companion object {
        val BbwChan =
            LynxChanSite(
                providerId = ProviderId("bbwchan"),
                displayName = "BBW Chan",
                siteUrl = "https://bbw-chan.link",
                nsfwByDefault = true,
            )
    }
}
