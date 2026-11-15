package com.orbin.feature.search

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.viewinterop.AndroidView
import com.orbin.core.model.Board
import com.orbin.provider.api.FourchanSearchSession
import com.orbin.provider.api.fourchanSearchInspectionScript
import com.orbin.provider.api.isFourchanWebSearchUrl
import com.orbin.uinext.SearchRow
import com.orbin.uinext.SearchState
import com.orbin.uinext.WebSearchResultsPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The owning ViewModel retains both results and the actual native verification page across Back. */
@Composable
internal fun FourchanWebSearch(
    query: String,
    browser: RetainedSearchBrowser,
    boards: List<Board>,
    onOpenThread: (String, String, Long, String) -> Unit,
    onOpenPost: ((String, String, Long, Long, String) -> Unit)? = null,
) {
    val session = browser.session
    val provider = session.provider
    val uriHandler = LocalUriHandler.current
    val state by session.state.collectAsState()
    LaunchedEffect(session, query) {
        if (query.isNotBlank() &&
            query != session.state.value.query
        ) {
            session.search(query)
        }
    }
    LaunchedEffect(state.requestUrl, state.requestId, state.loading) {
        if (state.loading) {
            delay(FOURCHAN_SEARCH_TIMEOUT_MILLIS)
            session.fail(state.requestUrl, "4chan search timed out. Retry the search.", state.requestId)
        }
    }
    val rows =
        state.hits.map { hit ->
            val key = hit.link.key
            SearchRow(
                "${key.provider.value}/${key.board.value}/${key.thread.value}/${hit.link.post?.value ?: 0}",
                hit.subject.ifBlank { "No. ${hit.link.key.thread.value}" },
                "${provider.displayName} · /${hit.link.key.board.value}/",
                hit.comment,
            )
        }
    val hits = rows.zip(state.hits).associate { it.first.id to it.second }
    val presentation =
        when {
            state.query.isBlank() -> SearchState.Idle
            rows.isNotEmpty() -> SearchState.Results(rows)
            state.error != null -> SearchState.Error(state.error!!)
            state.loading || state.verifying -> SearchState.Loading
            boards.isEmpty() ->
                SearchState.Error(
                    "Site metadata is unavailable. Open Boards to refresh it, then retry.",
                )
            else -> SearchState.Results(rows)
        }
    WebSearchResultsPane(
        state = presentation,
        onOpen = { row ->
            hits[row.id]?.let { hit ->
                val key = hit.link.key
                val post = hit.link.post
                if (post != null &&
                    onOpenPost != null
                ) {
                    onOpenPost(key.provider.value, key.board.value, key.thread.value, post.value, row.title)
                } else {
                    onOpenThread(key.provider.value, key.board.value, key.thread.value, row.title)
                }
            }
        },
        onRetry = session::retry,
        verifying = state.verifying,
        canLoadMore = state.nextOffset != null && !state.loading,
        onLoadMore = session::more,
        error = state.error,
        onOpenBrowser = state.requestUrl?.let { url -> { uriHandler.openUri(url) } },
    ) { modifier ->
        AndroidView(modifier = modifier, factory = {
            (browser.view.parent as? ViewGroup)?.removeView(browser.view)
            browser.view
        })
    }
}

/** Application context avoids retaining the Activity while the Search destination is covered. */
@SuppressLint("SetJavaScriptEnabled")
internal class RetainedSearchBrowser(
    context: Context,
    val session: FourchanSearchSession,
    scope: CoroutineScope,
) {
    private var requestId = -1L
    private var closed = false
    val view =
        WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.javaScriptCanOpenWindowsAutomatically = false
            webViewClient =
                object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = request.isForMainFrame && !isFourchanWebSearchUrl(request.url.toString())

                    override fun onPageStarted(
                        view: WebView,
                        url: String?,
                        favicon: Bitmap?,
                    ) {
                        session.started(url, requestId)
                    }

                    override fun onPageFinished(
                        view: WebView,
                        url: String?,
                    ) {
                        if (closed || url == null) return
                        if (view.url != url || view.progress < WEB_PAGE_PROGRESS_COMPLETE) return
                        if (!isFourchanWebSearchUrl(url)) return
                        val token = requestId
                        val offset = session.state.value.offset
                        view.evaluateJavascript(
                            fourchanSearchInspectionScript(offset),
                        ) { payload -> session.page(url, payload, token) }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (!closed &&
                            request.isForMainFrame
                        ) {
                            session.fail(
                                request.url.toString(),
                                "4chan search could not load. Retry the search.",
                                requestId,
                            )
                        }
                    }
                }
        }
    private val requests =
        scope.launch {
            session.state.map { it.requestUrl to it.requestId }.distinctUntilChanged().collect { (url, token) ->
                if (!closed && url != null && token != requestId) {
                    view.stopLoading()
                    requestId = token
                    view.loadUrl(url)
                }
            }
        }

    fun close() {
        if (closed) return
        closed = true
        requests.cancel()
        view.stopLoading()
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }
}

private const val FOURCHAN_SEARCH_TIMEOUT_MILLIS = 25_000L
private const val WEB_PAGE_PROGRESS_COMPLETE = 100
