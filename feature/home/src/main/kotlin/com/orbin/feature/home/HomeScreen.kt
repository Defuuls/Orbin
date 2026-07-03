package com.orbin.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.Board
import com.orbin.core.model.hiddenTagTokens
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Home screen: the board list for the active provider. Tapping a board opens its catalog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenBoard: (provider: String, board: String, title: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBoardGallery: () -> Unit,
    onOpenVanadiumBrowser: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val favoriteBoardIds by viewModel.favoriteBoardIds.collectAsStateWithLifecycle()
    val subscribedBoardIds by viewModel.subscribedBoardIds.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val onSettingsIconTap = rememberSettingsIconTapHandler(onOpenSettings, onOpenVanadiumBrowser)
    val openBoard: (Board) -> Unit = { board ->
        onOpenBoard(viewModel.providerId, board.id.value, board.title)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Orbin") },
                actions = {
                    IconButton(onClick = onOpenBoardGallery) {
                        Icon(Icons.Filled.GridView, contentDescription = "Board gallery")
                    }
                    IconButton(onClick = onSettingsIconTap) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                HomeUiState.Loading -> LoadingView()
                is HomeUiState.Error -> ErrorView(state.message, onRetry = viewModel::load)
                is HomeUiState.Success ->
                    BoardList(
                        boards = state.boards,
                        personalizedHomeFeed = settings.personalizedHomeFeed,
                        hiddenTags = settings.hiddenTagTokens(),
                        hideNsfwBoards = settings.hideNsfwBoards,
                        favoriteBoardIds = favoriteBoardIds,
                        subscribedBoardIds = subscribedBoardIds,
                        onBoardClick = openBoard,
                        onFavoriteChange = viewModel::setFavorite,
                        onSubscriptionChange = viewModel::setSubscribed,
                    )
            }
        }
    }
}

@Composable
private fun BoardList(
    boards: List<Board>,
    personalizedHomeFeed: Boolean,
    hiddenTags: Set<String>,
    hideNsfwBoards: Boolean,
    favoriteBoardIds: Set<String>,
    subscribedBoardIds: Set<String>,
    onBoardClick: (Board) -> Unit,
    onFavoriteChange: (board: String, favorite: Boolean) -> Unit,
    onSubscriptionChange: (board: String, subscribed: Boolean) -> Unit,
) {
    val filteredBoards =
        boards
            .filterNot { board -> hideNsfwBoards && board.isNsfw }
            .filterNot { board -> board.matchesAny(hiddenTags) }
            .let { visibleBoards ->
                if (!personalizedHomeFeed) {
                    visibleBoards
                } else {
                    visibleBoards.sortedWith(
                        compareByDescending<Board> { it.id.value in favoriteBoardIds }
                            .thenByDescending { it.id.value in subscribedBoardIds }
                            .thenBy { it.id.value },
                    )
                }
            }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(filteredBoards, key = { it.id.value }) { board ->
            val isFavorite = board.id.value in favoriteBoardIds
            val isSubscribed = board.id.value in subscribedBoardIds
            ListItem(
                modifier = Modifier.clickable { onBoardClick(board) },
                headlineContent = { Text("/${board.id.value}/ - ${board.title}") },
                supportingContent = {
                    if (board.description.isNotBlank()) Text(board.description)
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (board.isNsfw) Text("NSFW")
                        IconButton(onClick = { onSubscriptionChange(board.id.value, !isSubscribed) }) {
                            Icon(
                                imageVector =
                                    if (isSubscribed) {
                                        Icons.Filled.Notifications
                                    } else {
                                        Icons.Outlined.NotificationsNone
                                    },
                                contentDescription =
                                    if (isSubscribed) {
                                        "Unsubscribe board"
                                    } else {
                                        "Subscribe board"
                                    },
                            )
                        }
                        IconButton(onClick = { onFavoriteChange(board.id.value, !isFavorite) }) {
                            Icon(
                                imageVector =
                                    if (isFavorite) {
                                        Icons.Filled.Star
                                    } else {
                                        Icons.Outlined.StarBorder
                                    },
                                contentDescription =
                                    if (isFavorite) {
                                        "Remove favorite"
                                    } else {
                                        "Favorite board"
                                    },
                            )
                        }
                    }
                },
            )
            HorizontalDivider()
        }
    }
}

private fun Board.matchesAny(tokens: Set<String>): Boolean {
    if (tokens.isEmpty()) return false
    val haystack = listOf(id.value, title, description).joinToString(" ").lowercase()
    return tokens.any(haystack::contains)
}

@Composable
internal fun rememberSettingsIconTapHandler(
    onOpenSettings: () -> Unit,
    onOpenVanadiumBrowser: () -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    var settingsTapCount by rememberSaveable { mutableStateOf(0) }
    var pendingOpenSettings by remember { mutableStateOf<Job?>(null) }

    return {
        pendingOpenSettings?.cancel()
        settingsTapCount += 1
        if (settingsTapCount >= SECRET_TAP_COUNT) {
            settingsTapCount = 0
            onOpenVanadiumBrowser()
        } else {
            pendingOpenSettings =
                scope.launch {
                    delay(SECRET_TAP_WINDOW_MS)
                    settingsTapCount = 0
                    onOpenSettings()
                }
        }
    }
}

private const val SECRET_TAP_COUNT = 5
private const val SECRET_TAP_WINDOW_MS = 650L
