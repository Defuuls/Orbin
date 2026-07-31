package com.orbin.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.designsystem.component.ModernListItem
import com.orbin.core.designsystem.component.ModernSmallTopAppBar
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
            ModernSmallTopAppBar(
                title = "Subscriptions",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        items(boards, key = { it.id.value }) { board ->
            val isSubscribed = board.id.value in subscribedBoardIds
            ModernListItem(
                title = "/${board.id.value}/ - ${board.title}",
                subtitle = board.description.takeIf { it.isNotBlank() },
                trailing = {
                    Switch(
                        checked = isSubscribed,
                        onCheckedChange = { onSubscriptionChange(board.id.value, it) },
                    )
                },
                onClick = { onSubscriptionChange(board.id.value, !isSubscribed) },
            )
        }
    }
}
