package com.orbin.provider.lynxchan

import com.orbin.core.model.ProviderId

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
    val nsfwByDefault: Boolean = false,
) {
    /** Retrofit needs a trailing-slash base URL. */
    val apiBaseUrl: String get() = "$siteUrl/"

    companion object {
        val EightKun =
            LynxChanSite(
                providerId = ProviderId("8kun"),
                displayName = "8kun",
                siteUrl = "https://8kun.top",
                nsfwByDefault = true,
            )

        val BbwChan =
            LynxChanSite(
                providerId = ProviderId("bbwchan"),
                displayName = "BBW Chan",
                siteUrl = "https://bbw-chan.link",
                nsfwByDefault = true,
            )
    }
}
