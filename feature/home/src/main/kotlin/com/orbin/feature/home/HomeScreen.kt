package com.orbin.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.Board
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView
import kotlinx.coroutines.launch

/** Home screen: the board list for the active provider. Tapping a board opens its catalog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenBoard: (provider: String, board: String, title: String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val favoriteBoardIds by viewModel.favoriteBoardIds.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openBoard: (Board) -> Unit = { board ->
        onOpenBoard(viewModel.providerId, board.id.value, board.title)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            val state = uiState
            if (state is HomeUiState.Success) {
                BoardSetupDrawer(
                    providerName = state.providerName,
                    boards = state.boards,
                    favoriteBoardIds = favoriteBoardIds,
                    onFavoriteChange = viewModel::setFavorite,
                    onOpenBoard = { board ->
                        scope.launch { drawerState.close() }
                        openBoard(board)
                    },
                    onClose = { scope.launch { drawerState.close() } },
                )
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = { Text("Orbin") },
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Tune, contentDescription = "Board setup")
                        }
                        IconButton(onClick = onOpenSettings) {
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
                            favoriteBoardIds = favoriteBoardIds,
                            onBoardClick = openBoard,
                            onFavoriteChange = viewModel::setFavorite,
                        )
                }
            }
        }
    }
}

@Composable
private fun BoardList(
    boards: List<Board>,
    favoriteBoardIds: Set<String>,
    onBoardClick: (Board) -> Unit,
    onFavoriteChange: (board: String, favorite: Boolean) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(boards, key = { it.id.value }) { board ->
            val isFavorite = board.id.value in favoriteBoardIds
            ListItem(
                modifier = Modifier.clickable { onBoardClick(board) },
                headlineContent = { Text("/${board.id.value}/ - ${board.title}") },
                supportingContent = {
                    if (board.description.isNotBlank()) Text(board.description)
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (board.isNsfw) Text("NSFW")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardSetupDrawer(
    providerName: String,
    boards: List<Board>,
    favoriteBoardIds: Set<String>,
    onFavoriteChange: (board: String, favorite: Boolean) -> Unit,
    onOpenBoard: (Board) -> Unit,
    onClose: () -> Unit,
) {
    val favoriteBoards = boards.filter { it.id.value in favoriteBoardIds }
    val randomBoard = favoriteBoards.randomOrNull() ?: boards.randomOrNull()

    ModalDrawerSheet(modifier = Modifier.widthIn(max = 360.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Board setup", fontWeight = FontWeight.Bold)
                    Text(providerName)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            OutlinedButton(
                onClick = { randomBoard?.let(onOpenBoard) },
                enabled = randomBoard != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Casino, contentDescription = null)
                Text(text = "Random board", modifier = Modifier.padding(start = 8.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Favorites", fontWeight = FontWeight.Bold)
                if (favoriteBoards.isEmpty()) {
                    Text("No favorite boards")
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        favoriteBoards.forEach { board ->
                            AssistChip(
                                onClick = { onOpenBoard(board) },
                                label = { Text("/${board.id.value}/") },
                                trailingIcon = {
                                    IconButton(onClick = { onFavoriteChange(board.id.value, false) }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Remove favorite")
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Boards", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    boards.forEach { board ->
                        val isFavorite = board.id.value in favoriteBoardIds
                        FilterChip(
                            selected = isFavorite,
                            onClick = { onFavoriteChange(board.id.value, !isFavorite) },
                            label = { Text("/${board.id.value}/") },
                            leadingIcon =
                                if (isFavorite) {
                                    { Icon(Icons.Filled.Star, contentDescription = null) }
                                } else {
                                    null
                                },
                        )
                    }
                }
            }
        }
    }
}
