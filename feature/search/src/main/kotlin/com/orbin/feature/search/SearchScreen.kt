package com.orbin.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.uinext.GroupedDivider
import com.orbin.uinext.GroupedSection
import com.orbin.uinext.MetaLine
import com.orbin.uinext.NextEmpty
import com.orbin.uinext.NextError
import com.orbin.uinext.NextLoading
import com.orbin.uinext.NextTextField
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ScreenTitle
import com.orbin.uinext.next
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType

/** Search: one field, searched across the catalogs of the boards you follow. */
@Composable
fun SearchScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    NextTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ),
        ) {
            ScreenTitle(text = stringResource(R.string.search_title))
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = NextSpace.gutter)) {
                NextTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.search_query_label),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                SearchResults(state = state, onOpenThread = onOpenThread)
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchUiState,
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
) {
    when (state) {
        SearchUiState.Idle -> NextEmpty(stringResource(R.string.search_idle_hint))
        SearchUiState.Loading -> NextLoading()
        is SearchUiState.Error -> NextError(state.message)
        is SearchUiState.Results ->
            if (state.results.isEmpty()) {
                NextEmpty(stringResource(R.string.search_no_matches))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
                    item {
                        GroupedSection {
                            state.results.forEachIndexed { index, result ->
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onOpenThread(
                                                    result.key.provider.value,
                                                    result.key.board.value,
                                                    result.key.thread.value,
                                                    result.title,
                                                )
                                            }.padding(horizontal = NextSpace.rowX, vertical = NextSpace.rowY),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = result.title,
                                            style = NextType.body,
                                            color = next.ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        Text(
                                            text = "/${result.key.board.value}/",
                                            style = NextType.caption1,
                                            color = next.muted,
                                            maxLines = 1,
                                        )
                                    }
                                    MetaLine(result.snippet, maxLines = 2)
                                }
                                if (index < state.results.lastIndex) GroupedDivider()
                            }
                        }
                    }
                }
            }
    }
}
