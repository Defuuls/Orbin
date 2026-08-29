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

/** Tick the boards you want in the feed. That is the whole of this build's configuration. */
@Composable
fun MinimalBoardsScreen(
    onBack: () -> Unit,
    viewModel: MinimalBoardsViewModel = hiltViewModel(),
) {
    val boards by viewModel.boards.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    MinimalBoardsContent(
        boards = boards,
        isLoading = isLoading,
        errorMessage = errorMessage,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onToggle = { id, subscribed -> viewModel.setSubscribed(id, subscribed) },
    )
}

/**
 * The board picker's rendering, detached from its view model so it can be screenshot-tested.
 *
 * The screen itself is the full client's [BoardPickerScreen]; this is only the join, the same
 * shape as the feed's. The rail's one affordance is Feed, which is where you came from and the
 * only other place this app has — it is also the whole of the back navigation, which is why the
 * empty and failed states carry the rail too. Without it a fresh install, which has no boards to
 * list, would be a screen you could not leave.
 */
@Composable
internal fun MinimalBoardsContent(
    boards: List<SubscribableBoard>,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (BoardId, Boolean) -> Unit,
) {
    NextTheme {
        val title = stringResource(R.string.minimal_boards_title)
        // An empty list is not the same as a pending one. Showing a spinner for both left a fetch
        // that failed, or a provider with no boards, turning forever with no way to retry.
        if (boards.isEmpty()) {
            MessageScreen(
                title = title,
                subtitle =
                    when {
                        isLoading -> stringResource(R.string.minimal_boards_loading)
                        errorMessage != null ->
                            errorMessage.ifBlank { stringResource(R.string.minimal_boards_load_failed) }
                        else -> stringResource(R.string.minimal_no_boards_available)
                    },
                actionLabel = stringResource(R.string.minimal_refresh).takeUnless { isLoading },
                onAction = onRefresh,
                where = title,
                action = stringResource(R.string.minimal_feed_title),
                onSearch = onBack,
            )
            return@NextTheme
        }
        val choices =
            remember(boards) {
                boards.map { entry ->
                    BoardChoice(
                        id = entry.board.id.value,
                        title = entry.board.title,
                        subscribed = entry.isSubscribed,
                    )
                }
            }
        BoardPickerScreen(
            boards = choices,
            railAction = stringResource(R.string.minimal_feed_title),
            onToggle = { choice -> onToggle(BoardId(choice.id), !choice.subscribed) },
            onRefresh = onRefresh,
            onSearch = onBack,
        )
    }
}
