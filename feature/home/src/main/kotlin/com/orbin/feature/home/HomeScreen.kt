package com.orbin.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.Board
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView

/**
 * Home screen: the board list for the active provider. Tapping a board opens its catalog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenBoard: (provider: String, board: String, title: String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Orbin") },
                actions = {
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
                        onBoardClick = { board ->
                            onOpenBoard(viewModel.providerId, board.id.value, board.title)
                        },
                    )
            }
        }
    }
}

@Composable
private fun BoardList(
    boards: List<Board>,
    onBoardClick: (Board) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(boards, key = { it.id.value }) { board ->
            ListItem(
                modifier = Modifier.clickable { onBoardClick(board) },
                headlineContent = { Text("/${board.id.value}/ — ${board.title}") },
                supportingContent = {
                    if (board.description.isNotBlank()) Text(board.description)
                },
                trailingContent =
                    if (board.isNsfw) {
                        { Text("NSFW") }
                    } else {
                        null
                    },
            )
            HorizontalDivider()
        }
    }
}
