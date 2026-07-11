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
        val BbwChan =
            LynxChanSite(
                providerId = ProviderId("bbwchan"),
                displayName = "BBW Chan",
                siteUrl = "https://bbw-chan.link",
                nsfwByDefault = true,
            )

        /**
         * 8chan.moe (a.k.a. 8kun's successor "8moe"). A LynxChan instance whose responses sit
         * behind a POWBlock proof-of-work gate and a terms-of-service redirect; both are cleared
         * transparently by the network layer's POWBlock interceptor, so no provider-side handling
         * is required here.
         */
        val EightChan =
            LynxChanSite(
                providerId = ProviderId("eightchan"),
                displayName = "8chan.moe",
                siteUrl = "https://8chan.moe",
                nsfwByDefault = true,
            )
    }
}
