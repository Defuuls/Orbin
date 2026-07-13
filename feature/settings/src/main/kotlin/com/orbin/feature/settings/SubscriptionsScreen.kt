package com.orbin.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.Board
import com.orbin.core.ui.state.EmptyView
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView

/**
 * Settings sub-screen for managing board subscriptions. Toggling a board subscribes/unsubscribes
 * it for the active provider. Relocated here from the board-setup overlay so all subscribe controls
 * live under Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val subscribedBoardIds by viewModel.subscribedBoardIds.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subscriptions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                SubscriptionsUiState.Loading -> LoadingView()
                is SubscriptionsUiState.Error ->
                    ErrorView("Failed to load boards: ${state.message}", onRetry = viewModel::load)
                is SubscriptionsUiState.Success ->
                    if (state.boards.isEmpty()) {
                        EmptyView("No boards available")
                    } else {
                        SubscriptionsList(
                            boards = state.boards,
                            subscribedBoardIds = subscribedBoardIds,
                            onSubscriptionChange = viewModel::setSubscribed,
                        )
                    }
            }
        }
    }
}

@Composable
private fun SubscriptionsList(
    boards: List<Board>,
    subscribedBoardIds: Set<String>,
    onSubscriptionChange: (board: String, subscribed: Boolean) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(boards, key = { it.id.value }) { board ->
            val isSubscribed = board.id.value in subscribedBoardIds
            ListItem(
                modifier = Modifier.clickable { onSubscriptionChange(board.id.value, !isSubscribed) },
                headlineContent = { Text("/${board.id.value}/ - ${board.title}") },
                supportingContent = {
                    if (board.description.isNotBlank()) Text(board.description)
                },
                trailingContent = {
                    Switch(
                        checked = isSubscribed,
                        onCheckedChange = { onSubscriptionChange(board.id.value, it) },
                    )
                },
            )
            HorizontalDivider()
        }
    }
}
