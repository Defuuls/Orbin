package com.orbin.feature.browser

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.viewinterop.AndroidView
import com.orbin.core.common.link.InAppBrowser
import com.orbin.core.common.link.SafeExternalLinks
import com.orbin.domain.link.LinkPolicy
import com.orbin.uinext.components.NextTheme
import com.orbin.uinext.screens.BrowserActions
import com.orbin.uinext.screens.BrowserScreen

/**
 * The in-app browser, in its own process (see [InAppBrowser]). It opens only the safe HTTPS link
 * it was started with and never anything a page tries to navigate to outside [LinkPolicy].
 */
class BrowserActivity : ComponentActivity() {
    private val page = BrowserPage()
    private var webView: WebView? = null
    private var appearance =
        InAppBrowser.Appearance(
            dark = false,
            amoled = false,
            secure = true,
            closeWhenHidden = true,
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start = intent.dataString?.let(LinkPolicy::sanitize)
        if (start == null) {
            finish()
            return
        }
        appearance = InAppBrowser.appearance(intent)
        if (appearance.secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val view = browserWebView(this, page)
        webView = view
        if (savedInstanceState == null || view.restoreState(savedInstanceState) == null) view.loadUrl(start)
        setContent {
            NextTheme(darkTheme = appearance.dark, amoled = appearance.amoled) {
                BackHandler(enabled = page.chrome.canGoBack) { view.goBack() }
                BrowserScreen(
                    state = page.chrome,
                    actions =
                        BrowserActions(
                            onClose = ::finish,
                            onReload = view::reload,
                            onBack = { if (view.canGoBack()) view.goBack() },
                            onForward = { if (view.canGoForward()) view.goForward() },
                            onShare = { share(page.chrome.url.ifBlank { start }) },
                            onOpenExternal = { SafeExternalLinks.open(this, page.chrome.url.ifBlank { start }) },
                        ),
                ) { modifier ->
                    AndroidView(modifier = modifier, factory = {
                        (view.parent as? ViewGroup)?.removeView(view)
                        view
                    })
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView?.saveState(outState)
    }

    override fun onStop() {
        super.onStop()
        // Cookies are written lazily; flush so a login survives the process being killed.
        CookieManager.getInstance().flush()
        // With the app lock on, a browser left in the background would reopen without unlocking.
        if (appearance.closeWhenHidden && !isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun share(url: String) {
        val safe = LinkPolicy.sanitize(url) ?: return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, safe)
        startActivity(Intent.createChooser(send, null))
    }
}
