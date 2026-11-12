package com.orbin.ios

/**
 * Whether a WebM player's WKWebView (or the equivalent in tests) may navigate to [url].
 *
 * The page is a local `loadHTMLString` document whose only job is to play one video. The initial
 * load and [WKWebView.tearDownMedia] both use `about:blank` (and some WebKit versions report a
 * null or empty URL for `loadHTMLString` with a null base URL). Everything else — http(s), file,
 * javascript, data, blob, and other about: pages — is refused.
 */
internal fun shouldAllowWebMNavigation(url: String?): Boolean {
    if (url.isNullOrBlank()) return true
    val lower = url.trim().lowercase()
    return lower == "about:blank" ||
        lower == "about:srcdoc" ||
        lower.startsWith("about:blank#") ||
        lower.startsWith("about:blank?") ||
        lower.startsWith("about:srcdoc#") ||
        lower.startsWith("about:srcdoc?")
}
