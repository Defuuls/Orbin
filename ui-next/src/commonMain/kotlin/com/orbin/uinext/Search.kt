package com.orbin.uinext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier

/** A search hit: the thread's title, its board as `/g/`, and the start of its opening post. */
@Immutable
data class SearchRow(
    val id: String,
    val title: String,
    val board: String,
    val snippet: String,
)

/** Where a search is: nothing asked yet, running, answered, or failed with a message to show. */
@Immutable
sealed interface SearchState {
    data object Idle : SearchState

    data object Loading : SearchState

    data class Results(
        val rows: List<SearchRow>,
    ) : SearchState

    data class Error(
        val message: String,
    ) : SearchState
}

/**
 * Search: one field, searched across the catalogs of the boards the reader follows. The caller
 * holds [query] and runs the search on [onSearch] (the keyboard's search key); a tapped result
 * comes back as its row.
 */
@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    state: SearchState,
    onOpenRow: (SearchRow) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    ReaderSearchScreen(query, onQueryChange, onSearch, state, onOpenRow, modifier, onBack)
}
