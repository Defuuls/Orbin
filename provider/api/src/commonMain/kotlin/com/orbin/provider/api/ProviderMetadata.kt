package com.orbin.provider.api

import com.orbin.core.model.ProviderId

/**
 * Static description of a provider/engine instance. Surfaced in settings and the provider picker.
 */
data class ProviderMetadata(
    val id: ProviderId,
    val displayName: String,
    /** Base site URL, e.g. "https://example.org". Used for display and link resolution. */
    val baseUrl: String,
    val description: String = "",
    /** The engine family this provider targets, for grouping in the UI. */
    val engine: EngineKind = EngineKind.GENERIC,
    val isNsfwByDefault: Boolean = false,
    /** HTTPS hosts other than [baseUrl]'s that serve this provider's media and thumbnails. */
    val mediaHosts: Set<String> = emptySet(),
) {
    /**
     * Whether [url] is an https URL on this provider's own site or media hosts. Used to drop URLs
     * that came from outside the provider, such as an imported backup, before the app fetches them.
     */
    fun isOwnMediaUrl(url: String): Boolean {
        val parts = UriParts.parse(url) ?: return false
        val host = parts.host ?: return false
        if (!parts.scheme.equals("https", ignoreCase = true)) return false
        val siteHost = UriParts.parse(baseUrl)?.host
        return host.equals(siteHost, ignoreCase = true) || mediaHosts.any { it.equals(host, ignoreCase = true) }
    }
}

/** Known image board engine families. Adding a new one here is purely informational. */
enum class EngineKind {
    GENERIC,
    VICHAN,
    LYNXCHAN,
    TINYIB,
    FOURCHAN,
    WAKABA,
}

/**
 * Declares which operations a provider can perform. The app reads this to enable/disable UI such
 * as the search bar or board list, so a provider never has to throw "unsupported" at runtime for
 * features it advertises as off.
 */
data class ProviderCapabilities(
    val supportsBoardList: Boolean = true,
    val supportsCatalog: Boolean = true,
    val supportsThreads: Boolean = true,
    /** Server-side search across a board/site. */
    val supportsSearch: Boolean = false,
    /** Access to archived (expired) threads. */
    val supportsArchive: Boolean = false,
    /** Catalog pagination beyond a single page. */
    val supportsCatalogPaging: Boolean = true,
    val supportedSortOptions: Set<com.orbin.core.model.CatalogSort> =
        setOf(
            com.orbin.core.model.CatalogSort.BUMP_ORDER,
        ),
)
