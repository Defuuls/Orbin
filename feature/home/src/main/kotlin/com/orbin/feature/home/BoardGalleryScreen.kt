package com.orbin.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.Board
import com.orbin.core.model.matchesFilterTokens
import com.orbin.uinext.BoardTile
import com.orbin.uinext.BoardsScreen
import com.orbin.uinext.MessageScreen
import com.orbin.uinext.NextDestination
import com.orbin.uinext.NextTheme

/**
 * Boards destination: Next-styled tile grid with DestinationPill chrome.
 *
 * Subscriptions stay in onboarding / Run setup again. Tapping a tile opens that board; Surprise me
 * picks one at random.
 */
@Composable
fun BoardGalleryScreen(
    onOpenBoard: (provider: String, board: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenFeed: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenSearch: (() -> Unit)? = null,
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val providerId by viewModel.providerId.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribedBoardIds.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val openBoard: (Board) -> Unit = { board ->
        onOpenBoard(providerId, board.id.value, board.title)
    }

    val visibleBoards =
        remember(uiState, settings.hideNsfwBoards) {
            (uiState as? HomeUiState.Success)
                ?.boards
                ?.filterNot { board -> settings.hideNsfwBoards && board.isNsfw }
                // The permanent filter, with no reader tags on top of it.
                ?.filterNot { board -> board.matchesFilterTokens(emptySet()) }
                .orEmpty()
        }

    val tiles =
        remember(visibleBoards, subscribed) {
            visibleBoards.map { board ->
                BoardTile(
                    id = board.id.value,
                    path = "/${board.id.value}/",
                    title = board.title,
                    nsfw = board.isNsfw,
                    followed = board.id.value in subscribed,
                )
            }
        }

    val onDestination: ((NextDestination) -> Unit)? =
        if (onOpenFeed != null || onOpenDownloads != null || onOpenSettings != null) {
            { dest ->
                when (dest) {
                    NextDestination.FEED -> onOpenFeed?.invoke()
                    NextDestination.BOARDS -> Unit
                    NextDestination.DOWNLOADS -> onOpenDownloads?.invoke()
                    NextDestination.SETTINGS -> onOpenSettings?.invoke()
                }
            }
        } else {
            null
        }

    NextTheme {
        when (val state = uiState) {
            HomeUiState.Loading ->
                MessageScreen(
                    title = "Boards",
                    subtitle = "Loading…",
                    where = "Boards",
                    destination = NextDestination.BOARDS.takeIf { onDestination != null },
                    onDestination = onDestination,
                    modifier = modifier,
                )
            is HomeUiState.Error ->
                MessageScreen(
                    title = "Boards",
                    subtitle = state.message,
                    actionLabel = "Try again",
                    onAction = viewModel::load,
                    where = "Boards",
                    destination = NextDestination.BOARDS.takeIf { onDestination != null },
                    onDestination = onDestination,
                    modifier = modifier,
                )
            is HomeUiState.Success ->
                if (visibleBoards.isEmpty()) {
                    MessageScreen(
                        title = "Boards",
                        subtitle =
                            if (state.boards.isEmpty()) {
                                "No boards available"
                            } else {
                                "Every board is hidden by your board filters"
                            },
                        where = "Boards",
                        destination = NextDestination.BOARDS.takeIf { onDestination != null },
                        onDestination = onDestination,
                        modifier = modifier,
                    )
                } else {
                    BoardsScreen(
                        boards = tiles,
                        subtitle = "Find your people. Follow your interests.",
                        onFollowBoard = { board, checked -> viewModel.setSubscribed(board.id, checked) },
                        onOpenBoard = { tile ->
                            visibleBoards.firstOrNull { it.id.value == tile.id }?.let(openBoard)
                        },
                        onRandom = { visibleBoards.randomOrNull()?.let(openBoard) },
                        onOpenFeed = onOpenFeed,
                        onOpenDownloads = onOpenDownloads,
                        onOpenSettings = onOpenSettings,
                        onOpenSearch = onOpenSearch,
                        hideRailOnScroll = hideRailOnScroll,
                        onChromeVisibleChange = onChromeVisibleChange,
                        modifier = modifier,
                    )
                }
        }
    }
}
