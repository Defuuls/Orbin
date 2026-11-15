package com.orbin.ios

/**
 * Whether a WebM player's WKWebView (or the equivalent in tests) may navigate to [url].
 *
 * The page is a local `loadHTMLString` document whose only job is to play one video. The initial
 * load and [WKWebView.tearDownMedia] both use `about:blank` (and some WebKit versions report a
 * null or empty URL for `loadHTMLString` with a null base URL). Everything else — http(s), file,
 * javascript, data, blob, and other about: pages — is refused.
 */
internal fun shouldAllowWebMNavigation(
    url: String?,
    localDocumentUrl: String? = null,
): Boolean {
    if (url.isNullOrBlank()) return true
    // This exact app-created document is supplied only after staging an owned file. Giving a
    // WKWebView directory access must never become permission to navigate to arbitrary files.
    if (localDocumentUrl != null && url == localDocumentUrl) return true
    val lower = url.trim().lowercase()
    return lower == "about:blank" ||
        lower == "about:srcdoc" ||
        lower.startsWith("about:blank#") ||
        lower.startsWith("about:blank?") ||
        lower.startsWith("about:srcdoc#") ||
        lower.startsWith("about:srcdoc?")
}
