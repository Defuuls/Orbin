package com.orbin.domain.link

import com.orbin.provider.api.UriParts

/** Where a tapped link opens, decided once for every link source on both platforms. */
sealed interface LinkRoute {
    /** Open inside Orbin's own browser screen. */
    data class InApp(
        val url: String,
    ) : LinkRoute

    /** Hand to the system browser (Custom Tabs on Android, the default browser on iOS). */
    data class External(
        val url: String,
    ) : LinkRoute

    /** Not a well-formed HTTPS link: open nothing. */
    data object Rejected : LinkRoute
}

/**
 * The one HTTPS link rule. Comment links, file sources and "open in browser" actions all pass
 * through [sanitize] before anything is opened, and the in-app browser applies the same rule to
 * every page it navigates to, so a page cannot lead somewhere a tapped link could not.
 */
object LinkPolicy {
    /**
     * The trimmed link when it is a well-formed `https://` URL with a host, otherwise null.
     * Cleartext http, `javascript:`, `data:`, `intent:`, `file:` and every other scheme are
     * rejected, as are URLs [UriParts] cannot parse (spaces, backslashes, a missing host).
     */
    fun sanitize(url: String): String? {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return null
        val parts = UriParts.parse(trimmed) ?: return null
        return trimmed.takeIf { parts.scheme.equals("https", ignoreCase = true) && !parts.host.isNullOrBlank() }
    }

    /**
     * Where [url] opens. [openInApp] is the user's setting; [inAppAvailable] is false where this
     * platform build has no in-app browser, so links still open externally there.
     */
    fun route(
        url: String,
        openInApp: Boolean,
        inAppAvailable: Boolean,
    ): LinkRoute {
        val safe = sanitize(url) ?: return LinkRoute.Rejected
        return if (openInApp && inAppAvailable) LinkRoute.InApp(safe) else LinkRoute.External(safe)
    }

    /** Whether the in-app browser may load [url] as a page; anything else is blocked there. */
    fun allowsInAppPage(url: String): Boolean = sanitize(url) != null
}
