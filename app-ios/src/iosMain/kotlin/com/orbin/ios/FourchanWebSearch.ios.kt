@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.orbin.ios

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.orbin.provider.api.FourchanSearchSession
import com.orbin.provider.api.fourchanSearchInspectionScript
import com.orbin.provider.api.isFourchanWebSearchUrl
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private val searchWebsiteData by lazy { WKWebsiteDataStore.nonPersistentDataStore() }

/** Browser owns this native page, including verification DOM, while the reader covers Search. */
internal actual class NativeSearchTransport actual constructor(
    actual val session: FourchanSearchSession,
    scope: CoroutineScope,
) {
    private var requestId = -1L
    private var activeNavigation: WKNavigation? = null
    private var closed = false
    private val delegate =
        object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(
                webView: WKWebView,
                decidePolicyForNavigationAction: WKNavigationAction,
                decisionHandler: (WKNavigationActionPolicy) -> Unit,
            ) {
                val url = decidePolicyForNavigationAction.request.URL?.absoluteString
                val main = decidePolicyForNavigationAction.targetFrame?.mainFrame != false
                val allow = !main || url == "about:blank" || url != null && isFourchanWebSearchUrl(url)
                if (main && allow) session.started(url, requestId)
                val policy =
                    if (allow) {
                        WKNavigationActionPolicy.WKNavigationActionPolicyAllow
                    } else {
                        WKNavigationActionPolicy.WKNavigationActionPolicyCancel
                    }
                decisionHandler(policy)
            }

            @ObjCSignatureOverride
            override fun webView(
                webView: WKWebView,
                didStartProvisionalNavigation: WKNavigation?,
            ) {
                activeNavigation = didStartProvisionalNavigation
            }

            @ObjCSignatureOverride
            override fun webView(
                webView: WKWebView,
                didFinishNavigation: WKNavigation?,
            ) {
                if (closed || didFinishNavigation != activeNavigation) return
                val url = webView.URL?.absoluteString ?: return
                if (!isFourchanWebSearchUrl(url)) return
                val token = requestId
                webView.evaluateJavaScript(fourchanSearchInspectionScript(session.state.value.offset)) { value, error ->
                    if (error != null) {
                        session.fail(url, "4chan search could not be read. Retry the search.", token)
                    } else {
                        session.page(url, value as? String ?: "", token)
                    }
                }
            }

            override fun webView(
                webView: WKWebView,
                didFailProvisionalNavigation: WKNavigation?,
                withError: NSError,
            ) {
                if (!closed && didFailProvisionalNavigation == activeNavigation) {
                    session.fail(
                        webView.URL?.absoluteString ?: session.state.value.requestUrl,
                        "4chan search could not load. Retry the search.",
                        requestId,
                    )
                }
            }
        }
    private val view =
        WKWebView(
            CGRectMake(0.0, 0.0, 0.0, 0.0),
            WKWebViewConfiguration().apply {
                websiteDataStore = searchWebsiteData
                preferences.javaScriptCanOpenWindowsAutomatically = false
                defaultWebpagePreferences.allowsContentJavaScript = true
            },
        ).apply {
            navigationDelegate = delegate
            allowsLinkPreview = false
        }
    private val requests =
        scope.launch(Dispatchers.Main.immediate) {
            session.state.map { it.requestUrl to it.requestId }.distinctUntilChanged().collect { (url, token) ->
                if (!closed && url != null && requestId != token) {
                    view.stopLoading()
                    requestId = token
                    NSURL
                        .URLWithString(
                            url,
                        )?.let { activeNavigation = view.loadRequest(NSURLRequest.requestWithURL(it)) }
                }
            }
        }

    @Composable actual fun Content(modifier: Modifier) {
        UIKitView(factory = {
            view.removeFromSuperview()
            view
        }, modifier = modifier)
    }

    actual fun close() {
        if (!NSThread.isMainThread) {
            dispatch_async(dispatch_get_main_queue()) { close() }
            return
        }
        if (closed) return
        closed = true
        requests.cancel()
        view.stopLoading()
        view.navigationDelegate = null
        view.removeFromSuperview()
        view.loadHTMLString("", null)
    }
}
