package com.orbin.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.orbin.core.model.Board
import com.orbin.core.model.PostId
import com.orbin.core.model.ThreadKey
import com.orbin.provider.api.FourchanSearchSession
import com.orbin.uinext.SearchRow
import com.orbin.uinext.SearchState
import com.orbin.uinext.WebSearchResultsPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

@Composable
internal fun FourchanWebSearch(
    query: String,
    transport: NativeSearchTransport,
    boards: List<Board>,
    onOpen: (ThreadKey, PostId?) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val session = transport.session
    val provider = session.provider
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
        onOpen = { row -> hits[row.id]?.let { onOpen(it.link.key, it.link.post) } },
        onRetry = session::retry,
        verifying = state.verifying,
        canLoadMore = state.nextOffset != null && !state.loading,
        onLoadMore = session::more,
        error = state.error,
        onOpenBrowser = state.requestUrl?.let { url -> { uriHandler.openUri(url) } },
    ) { modifier -> transport.Content(modifier) }
}

/** A retained native transport belongs to Browser's lifetime, not a composable destination. */
internal expect class NativeSearchTransport(
    session: FourchanSearchSession,
    scope: CoroutineScope,
) {
    val session: FourchanSearchSession

    @Composable fun Content(modifier: Modifier)

    fun close()
}

private const val FOURCHAN_SEARCH_TIMEOUT_MILLIS = 25_000L
