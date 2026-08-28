package com.orbin.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.designsystem.component.ModernListItem
import com.orbin.core.designsystem.component.ModernSmallTopAppBar
import com.orbin.core.model.Board
import com.orbin.core.model.FeedThreadLimit
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
    val threadLimits by viewModel.threadLimits.collectAsStateWithLifecycle()

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
                            threadLimits = threadLimits,
                            onSubscriptionChange = viewModel::setSubscribed,
                            onThreadLimitChange = viewModel::setThreadLimit,
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
    threadLimits: Map<String, FeedThreadLimit>,
    onSubscriptionChange: (board: String, subscribed: Boolean) -> Unit,
    onThreadLimitChange: (board: String, limit: FeedThreadLimit?) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        items(boards, key = { it.id.value }) { board ->
            val isSubscribed = board.id.value in subscribedBoardIds
            val limit = threadLimits[board.id.value]
            ModernListItem(
                title = "/${board.id.value}/ - ${board.title}",
                // The override is stated on the row rather than hidden behind it, so a board
                // quietly capped at six threads is visible without opening anything.
                subtitle =
                    listOfNotNull(
                        board.description.takeIf { it.isNotBlank() },
                        limit?.let { "${it.label} threads in feed" },
                    ).joinToString(" · ").takeIf { it.isNotBlank() },
                trailing = {
                    Switch(
                        checked = isSubscribed,
                        onCheckedChange = { onSubscriptionChange(board.id.value, it) },
                    )
                },
                onClick = { onSubscriptionChange(board.id.value, !isSubscribed) },
            )
            if (isSubscribed) {
                ThreadLimitRow(
                    selected = limit,
                    onSelect = { chosen -> onThreadLimitChange(board.id.value, chosen) },
                )
            }
        }
    }
}

/**
 * How many of a board's threads reach the feed, overriding the global default.
 *
 * Shown only for a board you are subscribed to, because the setting has no effect on one you are
 * not: an unsubscribed board contributes no threads to limit.
 */
@Composable
private fun ThreadLimitRow(
    selected: FeedThreadLimit?,
    onSelect: (FeedThreadLimit?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Threads in feed",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("Default") },
        )
        FeedThreadLimit.entries.forEach { option ->
            Spacer(modifier = Modifier.width(6.dp))
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(option.label) },
            )
        }
    }
}
