package com.orbin.feature.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.orbin.domain.link.LinkPolicy
import com.orbin.uinext.screens.BrowserChromeState

/**
 * The browser's WebView settings. Pages get JavaScript and site storage, as in any browser, but
 * nothing that reaches into the device: no file or content access, no mixed content, no pop-up
 * windows, and Safe Browsing on. Saved logins persist in the browser process's own data
 * directory until "Clear local activity".
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun WebSettings.applyBrowserHardening() {
    javaScriptEnabled = true
    domStorageEnabled = true
    allowFileAccess = false
    allowContentAccess = false
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    // target=_blank and window.open load in this view, through the same navigation rule.
    setSupportMultipleWindows(false)
    javaScriptCanOpenWindowsAutomatically = false
    safeBrowsingEnabled = true
    setGeolocationEnabled(false)
    // Pages may zoom; the browser frame stays fixed.
    builtInZoomControls = true
    displayZoomControls = false
}

/**
 * Whether the browser must refuse to load [url]. Only pages a tapped link could open are allowed
 * ([LinkPolicy.allowsInAppPage]), so a page cannot send the browser to cleartext http, a local
 * file, or an `intent:` that launches another app.
 */
internal fun blocksNavigation(url: String): Boolean = !LinkPolicy.allowsInAppPage(url)

/** The page facts the browser frame shows, updated by the web view's callbacks. */
internal class BrowserPage {
    var chrome by mutableStateOf(BrowserChromeState())
        private set

    fun update(view: WebView) {
        chrome =
            chrome.copy(
                url = view.url.orEmpty(),
                title = view.title.orEmpty(),
                canGoBack = view.canGoBack(),
                canGoForward = view.canGoForward(),
            )
    }

    fun progress(percent: Int) {
        chrome = chrome.copy(progress = if (percent >= PAGE_COMPLETE) null else percent / PAGE_COMPLETE.toFloat())
    }
}

/** Builds the browser's web view, hardened and wired to [page]. */
internal fun browserWebView(
    context: Context,
    page: BrowserPage,
): WebView =
    WebView(context).apply {
        settings.applyBrowserHardening()
        // First-party logins work; trackers embedded in other sites' pages get no cookies.
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webViewClient = BrowserWebViewClient(page)
        webChromeClient = BrowserChromeClient(page)
        // Downloads are not offered: the browser never writes files.
        setDownloadListener { _, _, _, _, _ -> }
    }

internal class BrowserWebViewClient(
    private val page: BrowserPage,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean = blocksNavigation(request.url.toString())

    override fun onPageStarted(
        view: WebView,
        url: String?,
        favicon: Bitmap?,
    ) {
        page.update(view)
    }

    override fun onPageFinished(
        view: WebView,
        url: String?,
    ) {
        page.update(view)
    }

    override fun doUpdateVisitedHistory(
        view: WebView,
        url: String?,
        isReload: Boolean,
    ) {
        page.update(view)
    }
}

/** Denies every device permission a page can ask for, and file pickers. */
internal class BrowserChromeClient(
    private val page: BrowserPage,
) : WebChromeClient() {
    override fun onProgressChanged(
        view: WebView,
        newProgress: Int,
    ) {
        page.progress(newProgress)
    }

    override fun onReceivedTitle(
        view: WebView,
        title: String?,
    ) {
        page.update(view)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback,
    ) {
        callback.invoke(origin, false, false)
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean {
        filePathCallback.onReceiveValue(null)
        return true
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean = false
}

private const val PAGE_COMPLETE = 100
