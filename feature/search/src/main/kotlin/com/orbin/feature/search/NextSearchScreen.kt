package com.orbin.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.SearchResult
import com.orbin.uinext.NextTheme
import com.orbin.uinext.SearchRow
import com.orbin.uinext.SearchScreen
import com.orbin.uinext.SearchState

/** Search: one field, searched across the catalogs of the boards you follow. */
@Composable
fun NextSearchScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val byRow = remember(state) { (state as? SearchUiState.Results)?.results.orEmpty().associateBy { it.rowId } }

    NextTheme {
        SearchScreen(
            query = query,
            onQueryChange = { query = it },
            onSearch = { viewModel.search(query) },
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
                            board = "/${it.key.board.value}/",
                            snippet = it.snippet,
                        )
                    },
            )
    }
