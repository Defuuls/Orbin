package com.orbin.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.SearchResult
import com.orbin.uinext.components.NextTheme
import com.orbin.uinext.screens.SearchRow
import com.orbin.uinext.screens.SearchScreen
import com.orbin.uinext.screens.SearchState
import com.orbin.uinext.screens.WebSearchBlockedPane

/** Search: one field, searched across the catalogs of the boards you follow. */
@Composable
fun NextSearchScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    onOpenPost: ((provider: String, board: String, thread: Long, postId: Long, title: String) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val byRow = remember(state) { (state as? SearchUiState.Results)?.results.orEmpty().associateBy { it.rowId } }

    NextTheme {
        val webProvider = viewModel.webSearchProvider
        val webBoards = viewModel.webSearchBoards
        val fallbackBoards =
            remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.orbin.core.model.Board>()) }
        val boards by (webBoards ?: fallbackBoards).collectAsStateWithLifecycle()
        val webSearchAllowed by viewModel.webSearchAllowed.collectAsStateWithLifecycle()
        SearchScreen(
            // Blocked: a submitted query must not reach the session, which would start a page load.
            onWebSearch = if (webSearchAllowed) viewModel.webSearchSession?.let { it::submit } else ({}),
            webSearch =
                if (webProvider != null) {
                    { submitted ->
                        val browser = if (webSearchAllowed) viewModel.webSearchBrowser(LocalContext.current) else null
                        if (browser != null) {
                            FourchanWebSearch(submitted, browser, boards, onOpenThread, onOpenPost)
                        } else if (!webSearchAllowed) {
                            WebSearchBlockedPane()
                        }
                    }
                } else {
                    null
                },
            query = query,
            onQueryChange = { query = it },
            onSearch = { viewModel.search(query) },
            onBack = onBack,
            state = remember(state) { state.toSearchState() },
            onOpenRow = { row ->
                byRow[row.id]?.let { result ->
                    onOpenThread(
                        result.key.provider.value,
                        result.key.board.value,
                        result.key.thread.value,
                        result.title,
                    )
                }
            },
        )
    }
}

private val SearchResult.rowId: String get() = "${key.provider.value}/${key.board.value}/${key.thread.value}"

private fun SearchUiState.toSearchState(): SearchState =
    when (this) {
        SearchUiState.Idle -> SearchState.Idle
        SearchUiState.Loading -> SearchState.Loading
        is SearchUiState.Error -> SearchState.Error(message)
        is SearchUiState.Results ->
            SearchState.Results(
                results
                    .map {
                        SearchRow(
                            id = it.rowId,
                            title = it.title,
                            board = "${sourceLabel(it.key.provider.value)} · /${it.key.board.value}/",
                            snippet = it.snippet,
                        )
                    },
            )
    }

private fun sourceLabel(provider: String): String =
    when (provider) {
        "fourchan" -> "4chan"
        "bbwchan" -> "BBWChan"
        else -> provider
    }
