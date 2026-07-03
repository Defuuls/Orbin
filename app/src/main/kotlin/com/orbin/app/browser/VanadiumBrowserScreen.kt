package com.orbin.app.browser

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri

private const val DEFAULT_URL = "https://boards.4chan.org/"
private const val HTTP_SCHEME = "http"
private const val HTTPS_SCHEME = "https"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VanadiumBrowserScreen(onClose: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var address by rememberSaveable { mutableStateOf(DEFAULT_URL) }
    var pageTitle by rememberSaveable { mutableStateOf("Vanadium") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun navigate(rawInput: String) {
        val target = rawInput.toVanadiumUrl()
        address = target
        webView?.loadUrl(target)
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(pageTitle) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close browser")
                    }
                },
                actions = {
                    IconButton(enabled = canGoBack, onClick = { webView?.goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(enabled = canGoForward, onClick = { webView?.goForward() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Forward")
                    }
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { navigate(address) }) {
                            Icon(Icons.Filled.Language, contentDescription = "Go")
                        }
                    },
                )
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).also { view ->
                        webView = view
                        configureWebView(view)
                        view.webViewClient =
                            object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                ): Boolean {
                                    val target = request?.url?.toString().orEmpty()
                                    if (target.isBlank()) return true
                                    val safeTarget = target.toVanadiumUrl()
                                    if (safeTarget != target) {
                                        view?.loadUrl(safeTarget)
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageFinished(
                                    view: WebView?,
                                    url: String?,
                                ) {
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                    address = url?.toVanadiumUrl() ?: address
                                    pageTitle = view?.title?.takeIf { it.isNotBlank() } ?: "Vanadium"
                                }
                            }
                        view.loadUrl(DEFAULT_URL)
                    }
                },
                update = { view ->
                    canGoBack = view.canGoBack()
                    canGoForward = view.canGoForward()
                },
            )
        }
    }
}

@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled")
private fun configureWebView(webView: WebView) {
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        cacheMode = WebSettings.LOAD_NO_CACHE
        allowContentAccess = false
        allowFileAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        mediaPlaybackRequiresUserGesture = true
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        safeBrowsingEnabled = true
        setSupportMultipleWindows(false)
        javaScriptCanOpenWindowsAutomatically = false
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }
}

private fun String.toVanadiumUrl(): String {
    val trimmed = trim()
    if (trimmed.isBlank()) return DEFAULT_URL
    val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    val uri = candidate.toUri()
    val scheme = uri.scheme?.lowercase()
    return when (scheme) {
        null -> DEFAULT_URL
        HTTP_SCHEME -> "$HTTPS_SCHEME://${candidate.substringAfter("://")}"
        HTTPS_SCHEME -> candidate
        else -> DEFAULT_URL
    }
}
