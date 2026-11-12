package com.orbin.ios

import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject

/**
 * Shared WebM WKWebView setup: the in-memory data store already used for playback, inline media,
 * JavaScript only for the player's own scripts (popups and window.open are off), and no link
 * previews. File-URL access stays off because callers load HTML with a null base URL.
 *
 * The [WebMPageDelegate] must be retained by the caller: WKWebView holds a weak reference to its
 * navigation and UI delegates.
 */
internal fun webMViewConfiguration(): WKWebViewConfiguration =
    WKWebViewConfiguration().apply {
        allowsInlineMediaPlayback = true
        mediaTypesRequiringUserActionForPlayback = 0uL
        websiteDataStore = mediaDataStore
        preferences.javaScriptCanOpenWindowsAutomatically = false
        defaultWebpagePreferences.allowsContentJavaScript = true
    }

internal fun WKWebView.applyWebMPolicy(delegate: WebMPageDelegate) {
    navigationDelegate = delegate
    UIDelegate = delegate
    allowsLinkPreview = false
}

internal class WebMPageDelegate(
    private val onFinished: ((WKWebView) -> Unit)? = null,
) : NSObject(),
    WKNavigationDelegateProtocol,
    WKUIDelegateProtocol {
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL?.absoluteString
        decisionHandler(
            if (shouldAllowWebMNavigation(url)) {
                WKNavigationActionPolicy.WKNavigationActionPolicyAllow
            } else {
                WKNavigationActionPolicy.WKNavigationActionPolicyCancel
            },
        )
    }

    override fun webView(
        webView: WKWebView,
        didFinishNavigation: WKNavigation?,
    ) {
        onFinished?.invoke(webView)
    }

    override fun webView(
        webView: WKWebView,
        createWebViewWithConfiguration: WKWebViewConfiguration,
        forNavigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures,
    ): WKWebView? = null
}
