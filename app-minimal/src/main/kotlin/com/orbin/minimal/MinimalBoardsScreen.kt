package com.orbin.minimal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.BoardId
import com.orbin.uinext.BoardChoice
import com.orbin.uinext.BoardPickerScreen
import com.orbin.uinext.MessageScreen
import com.orbin.uinext.NextTheme

@Composable
fun MinimalBoardsScreen(
    onBack: () -> Unit,
    viewModel: MinimalBoardsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MinimalBoardsContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onToggle = { id, subscribed -> viewModel.setSubscribed(id, subscribed) },
    )
}

@Composable
internal fun MinimalBoardsContent(
    state: MinimalBoardsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (BoardId, Boolean) -> Unit,
) {
    NextTheme {
        val title = stringResource(R.string.minimal_boards_title)
        if (state.boards.isEmpty()) {
            MessageScreen(
                title = title,
                subtitle =
                    when {
                        state.isRefreshing -> stringResource(R.string.minimal_boards_loading)
                        state.refreshError != null ->
                            state.refreshError.ifBlank { stringResource(R.string.minimal_boards_load_failed) }
                        else -> stringResource(R.string.minimal_no_boards_available)
                    },
                actionLabel = stringResource(R.string.minimal_refresh).takeUnless { state.isRefreshing },
                onAction = onRefresh,
                where = title,
                action = stringResource(R.string.minimal_feed_title),
                onSearch = onBack,
            )
            return@NextTheme
        }

        val choices =
            remember(state.boards) {
                state.boards.map { entry ->
                    BoardChoice(
                        id = entry.board.id.value,
                        title = entry.board.title,
                        subscribed = entry.isSubscribed,
                    )
                }
            }
        val subscribed = remember(state.boards) { state.boards.count { it.isSubscribed } }
        val status =
            when {
                state.subscriptionError != null -> state.subscriptionError
                state.refreshError != null -> stringResource(R.string.minimal_boards_cached_error)
                state.isRefreshing -> stringResource(R.string.minimal_boards_refreshing)
                else -> stringResource(R.string.minimal_boards_summary, subscribed, state.boards.size)
            }

        BoardPickerScreen(
            boards = choices,
            subtitle = status,
            railAction = stringResource(R.string.minimal_feed_title),
            onToggle = { choice -> onToggle(BoardId(choice.id), !choice.subscribed) },
            onRefresh = onRefresh,
            onSearch = onBack,
        )
    }
}
