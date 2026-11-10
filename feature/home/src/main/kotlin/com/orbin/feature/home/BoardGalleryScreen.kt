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
import com.orbin.uinext.NextTheme
import com.orbin.uinext.PlatformSegments

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
    onOpenSaved: (() -> Unit)? = null,
    onOpenWatched: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val providerId by viewModel.providerId.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteBoardIds.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loadedSource = (uiState as? HomeUiState.Success)?.providerId
    val openBoard: (Board) -> Unit = { board ->
        loadedSource?.let { source -> onOpenBoard(source, board.id.value, board.title) }
    }

    val visibleBoards =
        remember(uiState, providerId, settings.hideNsfwBoards) {
            (uiState as? HomeUiState.Success)
                ?.takeIf { it.providerId == providerId }
                ?.boards
                ?.filterNot { board -> settings.hideNsfwBoards && board.isNsfw }
                // The permanent filter, with no reader tags on top of it.
                ?.filterNot { board -> board.matchesFilterTokens(emptySet()) }
                .orEmpty()
        }

    val tiles =
        remember(visibleBoards, favorites, providerId) {
            visibleBoards.map { board ->
                BoardTile(
                    id = board.id.value,
                    path = "/${board.id.value}/",
                    title = board.title,
                    nsfw = board.isNsfw,
                    followed = board.id.value in favorites,
                    site = providerId,
                )
            }
        }

    NextTheme {
        BoardsScreen(
            boards = tiles,
            headerContent = {
                if (viewModel.sites.size > 1) {
                    PlatformSegments(
                        labels = viewModel.sites.map { it.label },
                        selected = viewModel.sites.indexOfFirst { it.id == providerId },
                        onSelect = { index -> viewModel.selectSite(viewModel.sites[index].id) },
                    )
                }
            },
            onFollowBoard = { board, checked ->
                viewModel.setFavorite(board.id, checked, board.site ?: providerId)
            },
            onOpenBoard = { tile ->
                visibleBoards.firstOrNull { it.id.value == tile.id }?.let(openBoard)
            },
            onRandom =
                if (visibleBoards.isEmpty()) {
                    null
                } else {
                    { visibleBoards.randomOrNull()?.let(openBoard) }
                },
            onOpenFeed = onOpenFeed,
            onOpenDownloads = onOpenDownloads,
            onOpenSettings = onOpenSettings,
            onOpenSearch = onOpenSearch,
            onOpenSaved = onOpenSaved,
            onOpenWatched = onOpenWatched,
            hideRailOnScroll = hideRailOnScroll,
            onChromeVisibleChange = onChromeVisibleChange,
            emptyMessage =
                when (val state = uiState) {
                    HomeUiState.Loading -> "Loading…"
                    is HomeUiState.Error -> state.message
                    is HomeUiState.Success ->
                        if (state.boards.isEmpty()) {
                            "No boards available"
                        } else {
                            "Every board is hidden by your board filters"
                        }
                },
            onRetry = if (uiState is HomeUiState.Error) viewModel::load else null,
            modifier = modifier,
        )
    }
}
