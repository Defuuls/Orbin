package com.orbin.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.designsystem.component.ModernCardListItem
import com.orbin.core.designsystem.component.ModernCenterTopAppBar
import com.orbin.core.designsystem.component.ModernListItemHeader
import com.orbin.core.model.Board
import com.orbin.core.model.hiddenTagTokens
import com.orbin.core.model.matchesFilterTokens
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenBoard: (provider: String, board: String, title: String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val favoriteBoardIds by viewModel.favoriteBoardIds.collectAsStateWithLifecycle()
    val subscribedBoardIds by viewModel.subscribedBoardIds.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val providerId by viewModel.providerId.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ModernCenterTopAppBar(
                title = stringResource(R.string.home_boards_title),
                modifier =
                    Modifier.clickable(
                        onClickLabel = stringResource(R.string.home_scroll_to_top),
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    ),
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.home_settings))
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
                        hiddenTags = remember(settings.hiddenTags) { settings.hiddenTagTokens() },
                        hideNsfwBoards = settings.hideNsfwBoards,
                        favoriteBoardIds = favoriteBoardIds,
                        subscribedBoardIds = subscribedBoardIds,
                        onBoardClick = { board ->
                            onOpenBoard(providerId, board.id.value, board.title)
                        },
                        onFavoriteChange = viewModel::setFavorite,
                        onSubscriptionChange = viewModel::setSubscribed,
                        listState = listState,
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
    listState: LazyListState,
) {
    val filteredBoards =
        boards
            .filterNot { board -> hideNsfwBoards && board.isNsfw }
            .filterNot { board -> board.matchesFilterTokens(hiddenTags) }
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

    val favoriteBoards = filteredBoards.filter { it.id.value in favoriteBoardIds }
    val subscribedBoards =
        filteredBoards.filter {
            it.id.value in subscribedBoardIds && it.id.value !in favoriteBoardIds
        }
    val otherBoards = filteredBoards.filter { it.id.value !in favoriteBoardIds && it.id.value !in subscribedBoardIds }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (favoriteBoards.isNotEmpty()) {
            item {
                ModernListItemHeader(title = "Favorites")
            }
            items(favoriteBoards, key = { it.id.value }) { board ->
                BoardItemCard(
                    board = board,
                    isFavorite = true,
                    isSubscribed = board.id.value in subscribedBoardIds,
                    onBoardClick = { onBoardClick(board) },
                    onFavoriteChange = { onFavoriteChange(board.id.value, it) },
                    onSubscriptionChange = { onSubscriptionChange(board.id.value, it) },
                )
            }
        }

        if (subscribedBoards.isNotEmpty()) {
            item {
                ModernListItemHeader(title = "Subscribed")
            }
            items(subscribedBoards, key = { it.id.value }) { board ->
                BoardItemCard(
                    board = board,
                    isFavorite = false,
                    isSubscribed = true,
                    onBoardClick = { onBoardClick(board) },
                    onFavoriteChange = { onFavoriteChange(board.id.value, it) },
                    onSubscriptionChange = { onSubscriptionChange(board.id.value, it) },
                )
            }
        }

        if (otherBoards.isNotEmpty()) {
            item {
                ModernListItemHeader(title = stringResource(R.string.home_all_boards_header))
            }
            items(otherBoards, key = { it.id.value }) { board ->
                BoardItemCard(
                    board = board,
                    isFavorite = false,
                    isSubscribed = false,
                    onBoardClick = { onBoardClick(board) },
                    onFavoriteChange = { onFavoriteChange(board.id.value, it) },
                    onSubscriptionChange = { onSubscriptionChange(board.id.value, it) },
                )
            }
        }
    }
}

@Composable
private fun BoardItemCard(
    board: Board,
    isFavorite: Boolean,
    isSubscribed: Boolean,
    onBoardClick: () -> Unit,
    onFavoriteChange: (Boolean) -> Unit,
    onSubscriptionChange: (Boolean) -> Unit,
) {
    ModernCardListItem(
        title = "/${board.id.value}/ - ${board.title}",
        description = board.description.takeIf { it.isNotBlank() },
        trailing = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                AnimatedVisibility(visible = board.isNsfw, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        "NSFW",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                IconButton(
                    onClick = { onSubscriptionChange(!isSubscribed) },
                    modifier = Modifier.padding(4.dp),
                ) {
                    val notificationIcon =
                        if (isSubscribed) {
                            Icons.Filled.Notifications
                        } else {
                            Icons.Outlined.NotificationsNone
                        }
                    val notificationDesc = if (isSubscribed) "Unsubscribe" else "Subscribe"
                    val notificationTint =
                        if (isSubscribed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    Icon(
                        imageVector = notificationIcon,
                        contentDescription = notificationDesc,
                        tint = notificationTint,
                    )
                }
                IconButton(
                    onClick = { onFavoriteChange(!isFavorite) },
                    modifier = Modifier.padding(4.dp),
                ) {
                    val starIcon = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder
                    val starDesc =
                        if (isFavorite) {
                            stringResource(R.string.home_remove_favorite)
                        } else {
                            stringResource(R.string.home_favorite)
                        }
                    val starTint =
                        if (isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    Icon(
                        imageVector = starIcon,
                        contentDescription = starDesc,
                        tint = starTint,
                    )
                }
            }
        },
        onClick = onBoardClick,
    )
}
