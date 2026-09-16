package com.orbin.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.Board
import com.orbin.core.model.SavedSearch
import com.orbin.core.model.SearchContentType
import com.orbin.uinext.GroupedDivider
import com.orbin.uinext.GroupedSection
import com.orbin.uinext.InlineAction
import com.orbin.uinext.MetaLine
import com.orbin.uinext.NextConfirmDialog
import com.orbin.uinext.NextEmpty
import com.orbin.uinext.NextError
import com.orbin.uinext.NextLoading
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ScreenTitle
import com.orbin.uinext.next
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType

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
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NextSpace.gutter - 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                InlineAction(
                    label = stringResource(R.string.search_tab_search),
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                )
                InlineAction(
                    label = stringResource(R.string.search_tab_saved, savedSearches.size),
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
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
                        modifier = Modifier.fillMaxSize().padding(horizontal = NextSpace.gutter),
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
                        modifier = Modifier.fillMaxSize().padding(horizontal = NextSpace.gutter),
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
            label = { Text(stringResource(R.string.search_subscribed_board)) },
            placeholder = { Text(stringResource(R.string.search_all_subscribed_boards)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier =
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_all_subscribed_boards)) },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            boards.forEach { board ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_board_item, board.id.value, board.title)) },
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
            label = { Text(stringResource(R.string.search_query_label)) },
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
                label = { Text(stringResource(R.string.search_min_replies)) },
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
                Text(stringResource(R.string.search_include_nsfw))
            }
        }

        if (saveRecentSearches && recents.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                recents.forEach { recent ->
                    InlineAction(
                        label = recent,
                        onClick = { onSearch(recent) },
                    )
                }
            }
        }

        when (val s = state) {
            SearchUiState.Idle -> NextEmpty(stringResource(R.string.search_idle_hint))
            SearchUiState.Loading -> NextLoading()
            is SearchUiState.Error -> NextError(s.message)
            is SearchUiState.Results ->
                if (s.results.isEmpty()) {
                    NextEmpty(stringResource(R.string.search_no_matches))
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            InlineAction(
                                label = stringResource(R.string.search_save_this_search),
                                accent = true,
                                onClick = onSaveSearch,
                            )
                        }
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                GroupedSection {
                                    s.results.forEachIndexed { index, result ->
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
                                                    }.padding(
                                                        horizontal = NextSpace.rowX,
                                                        vertical = NextSpace.rowY,
                                                    ),
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
                                        if (index < s.results.lastIndex) GroupedDivider()
                                    }
                                }
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
    var pendingDelete by remember { mutableStateOf<SavedSearch?>(null) }

    Column(modifier = modifier) {
        if (savedSearches.isEmpty()) {
            NextEmpty(stringResource(R.string.search_no_saved))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(savedSearches, key = { it.id }) { search ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onLoadSearch(search) }
                                .padding(horizontal = NextSpace.rowX, vertical = NextSpace.rowY),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                search.text,
                                style = NextType.body,
                                color = next.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                search.board?.let { board ->
                                    Text(
                                        stringResource(R.string.search_board_slug, board.value),
                                        style = NextType.caption1,
                                        color = next.muted,
                                    )
                                }
                                if (search.filters.contentTypes.isNotEmpty()) {
                                    Text(
                                        search.filters.contentTypes.joinToString(", ") { it.label },
                                        style = NextType.caption1,
                                        color = next.muted,
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { pendingDelete = search }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.search_delete),
                                tint = next.accent,
                            )
                        }
                    }
                    GroupedDivider()
                }
            }
        }
    }

    pendingDelete?.let { search ->
        NextConfirmDialog(
            title = stringResource(R.string.search_delete_saved_title),
            message = stringResource(R.string.search_delete_saved_text, search.text),
            onConfirm = {
                onDeleteSearch(search.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
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
            InlineAction(
                label = type.label,
                selected = type in selected,
                onClick = { onToggle(type) },
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
