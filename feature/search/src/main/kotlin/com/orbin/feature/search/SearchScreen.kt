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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.Board
import com.orbin.core.model.SavedSearch
import com.orbin.core.model.SearchContentType
import com.orbin.core.ui.state.EmptyView
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView

/** Search screen: board-scoped catalog search with recent-query chips and saved searches. */
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
    val savedSearches by viewModel.savedSearches.collectAsStateWithLifecycle()
    var selectedBoard by remember { mutableStateOf<Board?>(null) }
    var query by remember { mutableStateOf("") }
    var contentTypes by remember { mutableStateOf(emptySet<SearchContentType>()) }
    var minReplies by remember { mutableStateOf("") }
    var includeNsfw by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(topBar = { TopAppBar(title = { Text("Search") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Search") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Saved (${savedSearches.size})") },
                )
            }

            when (selectedTab) {
                0 ->
                    SearchTabContent(
                        query = query,
                        onQueryChange = { query = it },
                        selectedBoard = selectedBoard,
                        onBoardSelected = { selectedBoard = it },
                        subscribedBoards = subscribedBoards,
                        contentTypes = contentTypes,
                        onToggleContentType = { type ->
                            contentTypes =
                                if (type in contentTypes) {
                                    contentTypes - type
                                } else {
                                    contentTypes + type
                                }
                        },
                        minReplies = minReplies,
                        onMinRepliesChange = { minReplies = it },
                        includeNsfw = includeNsfw,
                        onNsfwToggle = { includeNsfw = it },
                        recents = recents,
                        saveRecentSearches = saveRecentSearches,
                        state = state,
                        onSearch = { text ->
                            viewModel.search(
                                text,
                                selectedBoard?.id?.value.orEmpty(),
                                contentTypes,
                            )
                        },
                        onSaveSearch = {
                            viewModel.saveCurrentSearch(
                                query,
                                selectedBoard?.id?.value.orEmpty(),
                                contentTypes,
                                minReplies.toIntOrNull(),
                                includeNsfw,
                            )
                        },
                        onOpenThread = onOpenThread,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    )
                1 ->
                    SavedSearchesTabContent(
                        savedSearches = savedSearches,
                        onLoadSearch = { search ->
                            query = search.text
                            selectedBoard = subscribedBoards.find { it.id == search.board }
                            contentTypes = search.filters.contentTypes
                            minReplies = search.filters.minReplies?.toString() ?: ""
                            includeNsfw = search.filters.includeNsfw
                            selectedTab = 0
                            viewModel.loadSavedSearch(search)
                        },
                        onDeleteSearch = { id -> viewModel.deleteSavedSearch(id) },
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    )
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
private fun SearchTabContent(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedBoard: Board?,
    onBoardSelected: (Board?) -> Unit,
    subscribedBoards: List<Board>,
    contentTypes: Set<SearchContentType>,
    onToggleContentType: (SearchContentType) -> Unit,
    minReplies: String,
    onMinRepliesChange: (String) -> Unit,
    includeNsfw: Boolean,
    onNsfwToggle: (Boolean) -> Unit,
    recents: List<String>,
    saveRecentSearches: Boolean,
    state: SearchUiState,
    onSearch: (String) -> Unit,
    onSaveSearch: () -> Unit,
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        BoardDropdown(
            boards = subscribedBoards,
            selectedBoard = selectedBoard,
            onSelected = onBoardSelected,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search query") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(
                    onSearch = { onSearch(query) },
                ),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        SearchTypeFilters(
            selected = contentTypes,
            onToggle = onToggleContentType,
            modifier = Modifier.padding(top = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = minReplies,
                onValueChange = onMinRepliesChange,
                label = { Text("Min replies") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Checkbox(
                    checked = includeNsfw,
                    onCheckedChange = onNsfwToggle,
                )
                Text("Include NSFW")
            }
        }

        if (saveRecentSearches && recents.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                recents.forEach { recent ->
                    AssistChip(
                        onClick = {
                            onSearch(recent)
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        Button(
                            onClick = onSaveSearch,
                            modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                        ) {
                            Text("Save This Search")
                        }
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

@Composable
private fun SavedSearchesTabContent(
    savedSearches: List<SavedSearch>,
    onLoadSearch: (SavedSearch) -> Unit,
    onDeleteSearch: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (savedSearches.isEmpty()) {
            EmptyView("No saved searches")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(savedSearches, key = { it.id }) { search ->
                    ListItem(
                        modifier =
                            Modifier.clickable { onLoadSearch(search) },
                        headlineContent = { Text(search.text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                search.board?.let { board ->
                                    Text("/${board.value}/", style = MaterialTheme.typography.labelSmall)
                                }
                                if (search.filters.contentTypes.isNotEmpty()) {
                                    Text(
                                        search.filters.contentTypes.joinToString(", ") { it.label },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { onDeleteSearch(search.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                            }
                        },
                    )
                    HorizontalDivider()
                }
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
