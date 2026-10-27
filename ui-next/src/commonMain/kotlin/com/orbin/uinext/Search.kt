package com.orbin.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_clear_search
import com.orbin.uinext.resources.next_search_idle_hint
import com.orbin.uinext.resources.next_search_no_matches
import com.orbin.uinext.resources.next_search_query_label
import com.orbin.uinext.resources.next_search_title
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

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
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
    ) {
        ScreenTitle(text = stringResource(Res.string.next_search_title))
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = NextSpace.gutter)) {
            NextTextField(
                value = query,
                onValueChange = onQueryChange,
                label = stringResource(Res.string.next_search_query_label),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                trailing =
                    if (query.isNotEmpty()) {
                        {
                            NextIconAction(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.next_clear_search),
                                onClick = { onQueryChange("") },
                            )
                        }
                    } else {
                        null
                    },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            SearchResults(state = state, onOpenRow = onOpenRow)
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchState,
    onOpenRow: (SearchRow) -> Unit,
) {
    when (state) {
        SearchState.Idle -> NextEmpty(stringResource(Res.string.next_search_idle_hint))
        SearchState.Loading -> NextLoading()
        is SearchState.Error -> NextError(state.message)
        is SearchState.Results ->
            if (state.rows.isEmpty()) {
                NextEmpty(stringResource(Res.string.next_search_no_matches))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    groupedItems(state.rows, key = { it.id }) { _, row ->
                        SearchResultRow(row, onClick = { onOpenRow(row) })
                    }
                }
            }
    }
}

@Composable
private fun SearchResultRow(
    row: SearchRow,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .nextClickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = NextSpace.rowX, vertical = NextSpace.rowY),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BoardDot(row.board, size = 6.dp)
            Text(
                text = row.board,
                style = NextType.caption1,
                color = boardHue(row.board),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = row.title,
                style = NextType.body,
                color = next.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        MetaLine(row.snippet, maxLines = 2)
    }
}
