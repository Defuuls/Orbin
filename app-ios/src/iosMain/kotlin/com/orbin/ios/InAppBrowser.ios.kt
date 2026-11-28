@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.orbin.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.orbin.uinext.screens.BrowserChromeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.distantPast
import platform.WebKit.WKAudiovisualMediaTypeAll
import platform.WebKit.WKFrameInfo
import platform.WebKit.WKMediaCaptureType
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKNavigationResponse
import platform.WebKit.WKNavigationResponsePolicy
import platform.WebKit.WKPermissionDecision
import platform.WebKit.WKSecurityOrigin
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject
import kotlin.coroutines.resume

internal actual val supportsInAppBrowser: Boolean = true

/**
 * Where the browser keeps sites' cookies, storage and cache until "Clear local activity". This is
 * the system's persistent web store; nothing else in Orbin uses it (search and the video players
 * have their own throwaway stores), so it holds only pages opened in the browser.
 */
private val browserWebsiteData: WKWebsiteDataStore
    get() = WKWebsiteDataStore.defaultDataStore()

internal actual suspend fun clearInAppBrowserData() =
    withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            browserWebsiteData.removeDataOfTypes(WKWebsiteDataStore.allWebsiteDataTypes(), NSDate.distantPast) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

/**
 * The browser's web view settings. Pages get JavaScript and site storage, as in any browser, but
 * no pop-up windows, no media that plays before a tap, and fraudulent-site warnings stay on.
 * WebKit blocks third-party cookies and mixed content on its own.
 */
private fun browserViewConfiguration(): WKWebViewConfiguration =
    WKWebViewConfiguration().apply {
        websiteDataStore = browserWebsiteData
        allowsInlineMediaPlayback = true
        mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeAll
        preferences.javaScriptCanOpenWindowsAutomatically = false
        preferences.fraudulentWebsiteWarningEnabled = true
        defaultWebpagePreferences.allowsContentJavaScript = true
    }

/**
 * Applies [allowsInAppBrowserNavigation] to every load, refuses downloads, and denies the camera,
 * microphone and motion sensors. Location needs a usage description Orbin does not declare, so
 * WebKit refuses it without asking. File uploads open the system picker, which shares only what
 * the person picks; iOS gives apps no way to turn it off.
 */
private class BrowserPageDelegate :
    NSObject(),
    WKNavigationDelegateProtocol,
    WKUIDelegateProtocol {
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL?.absoluteString
        val mainFrame = decidePolicyForNavigationAction.targetFrame?.mainFrame != false
        decisionHandler(
            if (allowsInAppBrowserNavigation(url, mainFrame)) {
                WKNavigationActionPolicy.WKNavigationActionPolicyAllow
            } else {
                WKNavigationActionPolicy.WKNavigationActionPolicyCancel
            },
        )
    }

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationResponse: WKNavigationResponse,
        decisionHandler: (WKNavigationResponsePolicy) -> Unit,
    ) {
        // Whatever WebKit cannot show would become a download, and the browser never saves files.
        decisionHandler(
            if (decidePolicyForNavigationResponse.canShowMIMEType) {
                WKNavigationResponsePolicy.WKNavigationResponsePolicyAllow
            } else {
                WKNavigationResponsePolicy.WKNavigationResponsePolicyCancel
            },
        )
    }

    /** target=_blank and window.open load in this view, through the same navigation rule. */
    override fun webView(
        webView: WKWebView,
        createWebViewWithConfiguration: WKWebViewConfiguration,
        forNavigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures,
    ): WKWebView? {
        val url = forNavigationAction.request.URL?.absoluteString
        if (forNavigationAction.targetFrame == null && allowsInAppBrowserNavigation(url, mainFrame = true)) {
            webView.loadRequest(forNavigationAction.request)
        }
        return null
    }

    override fun webView(
        webView: WKWebView,
        requestMediaCapturePermissionForOrigin: WKSecurityOrigin,
        initiatedByFrame: WKFrameInfo,
        type: WKMediaCaptureType,
        decisionHandler: (WKPermissionDecision) -> Unit,
    ) {
        decisionHandler(WKPermissionDecision.WKPermissionDecisionDeny)
    }

    override fun webView(
        webView: WKWebView,
        requestDeviceOrientationAndMotionPermissionForOrigin: WKSecurityOrigin,
        initiatedByFrame: WKFrameInfo,
        decisionHandler: (WKPermissionDecision) -> Unit,
    ) {
        decisionHandler(WKPermissionDecision.WKPermissionDecisionDeny)
    }
}

private class NativeWebPage(
    url: String,
) : InAppWebPage {
    // WKWebView holds its delegates weakly; the page keeps this one alive.
    private val delegate = BrowserPageDelegate()

    val view: WKWebView =
        WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = browserViewConfiguration()).apply {
            navigationDelegate = delegate
            UIDelegate = delegate
            // Edge swipes close the browser, as they leave every other screen; the bar has back and forward.
            allowsBackForwardNavigationGestures = false
            // Previews open pages outside the navigation rule.
            allowsLinkPreview = false
            NSURL.URLWithString(url)?.let { loadRequest(NSURLRequest.requestWithURL(it)) }
        }

    override var chrome by mutableStateOf(BrowserChromeState(url = url))
        private set

    /** Reads the page's state; cheap property reads, so it also catches in-page address changes. */
    fun refreshChrome() {
        val next =
            webPageChrome(
                url = view.URL?.absoluteString,
                title = view.title,
                loading = view.loading,
                progress = view.estimatedProgress,
                canGoBack = view.canGoBack,
                canGoForward = view.canGoForward,
            )
        if (next != chrome) chrome = next
    }

    override fun reload() {
        view.reload()
    }

    override fun goBack() {
        if (view.canGoBack) view.goBack()
    }

    override fun goForward() {
        if (view.canGoForward) view.goForward()
    }

    /** Stops the page, its media included, as soon as the screen closes rather than when freed. */
    fun close() {
        view.stopLoading()
        view.navigationDelegate = null
        view.UIDelegate = null
        view.loadHTMLString("", baseURL = null)
    }

    @Composable
    override fun Content(modifier: Modifier) {
        UIKitView(factory = {
            view.removeFromSuperview()
            view
        }, modifier = modifier)
    }
}

@Composable
internal actual fun rememberInAppWebPage(url: String): InAppWebPage {
    val page = remember(url) { NativeWebPage(url) }
    LaunchedEffect(page) {
        while (true) {
            page.refreshChrome()
            delay(CHROME_REFRESH_MS)
        }
    }
    DisposableEffect(page) { onDispose(page::close) }
    return page
}

private const val CHROME_REFRESH_MS = 250L
