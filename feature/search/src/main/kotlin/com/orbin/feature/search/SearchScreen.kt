package com.orbin.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.Board
import com.orbin.core.model.SearchContentType
import com.orbin.core.ui.state.EmptyView
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView

/** Search screen: board-scoped catalog search with recent-query chips. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recents by viewModel.recentQueries.collectAsStateWithLifecycle()
    val subscribedBoards by viewModel.subscribedBoards.collectAsStateWithLifecycle()
    val saveRecentSearches by viewModel.saveRecentSearches.collectAsStateWithLifecycle()
    var selectedBoard by remember { mutableStateOf<Board?>(null) }
    var query by remember { mutableStateOf("") }
    var contentTypes by remember { mutableStateOf(emptySet<SearchContentType>()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Search") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            BoardDropdown(
                boards = subscribedBoards,
                selectedBoard = selectedBoard,
                onSelected = { selectedBoard = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search query") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(
                        onSearch = {
                            viewModel.search(query, selectedBoard?.id?.value.orEmpty(), contentTypes)
                        },
                    ),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            SearchTypeFilters(
                selected = contentTypes,
                onToggle = { type ->
                    contentTypes =
                        if (type in contentTypes) {
                            contentTypes - type
                        } else {
                            contentTypes + type
                        }
                },
                modifier = Modifier.padding(top = 8.dp),
            )

            if (saveRecentSearches && recents.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    recents.forEach { recent ->
                        AssistChip(
                            onClick = {
                                query = recent
                                viewModel.search(recent, selectedBoard?.id?.value.orEmpty(), contentTypes)
                            },
                            label = { Text(recent) },
                        )
                    }
                }
            }

            when (val s = state) {
                SearchUiState.Idle -> EmptyView("Search subscribed boards")
                SearchUiState.Loading -> LoadingView()
                is SearchUiState.Error -> ErrorView(s.message)
                is SearchUiState.Results ->
                    if (s.results.isEmpty()) {
                        EmptyView("No matches")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(s.results, key = { it.key.thread.value }) { result ->
                                ListItem(
                                    modifier =
                                        Modifier.clickable {
                                            onOpenThread(
                                                result.key.provider.value,
                                                result.key.board.value,
                                                result.key.thread.value,
                                                result.title,
                                            )
                                        },
                                    headlineContent = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = result.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            Text(
                                                text = "/${result.key.board.value}/",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    },
                                    supportingContent = { Text(result.snippet, maxLines = 2) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardDropdown(
    boards: List<Board>,
    selectedBoard: Board?,
    onSelected: (Board?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedBoard?.let { "/${it.id.value}/ - ${it.title}" }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Subscribed board") },
            placeholder = { Text("All subscribed boards") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("All subscribed boards") },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            boards.forEach { board ->
                DropdownMenuItem(
                    text = { Text("/${board.id.value}/ - ${board.title}") },
                    onClick = {
                        onSelected(board)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchTypeFilters(
    selected: Set<SearchContentType>,
    onToggle: (SearchContentType) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchContentType.entries.forEach { type ->
            FilterChip(
                selected = type in selected,
                onClick = { onToggle(type) },
                label = { Text(type.label) },
            )
        }
    }
}

private val SearchContentType.label: String
    get() =
        when (this) {
            SearchContentType.POST -> "Post"
            SearchContentType.IMAGE -> "Image"
            SearchContentType.VIDEO -> "Video"
            SearchContentType.AUDIO -> "Audio"
            SearchContentType.URL -> "URL"
        }
