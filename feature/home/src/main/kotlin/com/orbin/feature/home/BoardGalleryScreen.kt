package com.orbin.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.Board
import com.orbin.core.model.matchesFilterTokens
import com.orbin.feature.home.R
import com.orbin.provider.api.parseThreadLink
import com.orbin.uinext.BoardTile
import com.orbin.uinext.BoardsScreen
import com.orbin.uinext.NextTheme
import com.orbin.uinext.OpenThreadLinkDialog
import com.orbin.uinext.PlatformSegments

/**
 * Boards destination: Next-styled tile grid with DestinationPill chrome.
 *
 * Following includes boards chosen in first run and later in this directory. Tapping a tile opens
 * that board; Surprise me picks one at random.
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
    downloadBadge: Int = 0,
    viewModel: HomeViewModel = hiltViewModel(),
    onOpenSaved: (() -> Unit)? = null,
    onOpenWatched: (() -> Unit)? = null,
    onOpenLibrary: (() -> Unit)? = onOpenWatched,
    watchedBadge: Int = 0,
    onOpenThread: ((provider: String, board: String, thread: Long, title: String) -> Unit)? = null,
) {
    var linkDialog by rememberSaveable { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val providerId by viewModel.providerId.collectAsStateWithLifecycle()
    val followed by viewModel.followedBoards.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteBoards.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loadedSource = (uiState as? HomeUiState.Success)?.providerId
    val openBoard: (Board) -> Unit = { board ->
        loadedSource?.let { source -> onOpenBoard(source, board.id.value, board.title) }
    }

    val visibleBoards =
        remember(uiState, providerId, settings.nsfwContentHidden) {
            (uiState as? HomeUiState.Success)
                ?.takeIf { it.providerId == providerId }
                ?.boards
                ?.filterNot { board -> settings.nsfwContentHidden && board.isNsfw }
                // The permanent filter, with no reader tags on top of it.
                ?.filterNot { board -> board.matchesFilterTokens(emptySet()) }
                .orEmpty()
        }

    val tiles =
        remember(visibleBoards, followed, favorites, providerId) {
            visibleBoards.map { board ->
                BoardTile(
                    id = board.id.value,
                    path = "/${board.id.value}/",
                    title = board.title,
                    nsfw = board.isNsfw,
                    followed = followed.contains(providerId, board.id.value),
                    favorite = favorites.contains(providerId, board.id.value),
                    site = providerId,
                )
            }
        }

    NextTheme {
        BoardsScreen(
            onOpenLink = onOpenThread?.let { { linkDialog = true } },
            downloadBadge = downloadBadge,
            boards = tiles,
            headerContent = {
                if (viewModel.sites.size > 1) {
                    PlatformSegments(
                        labels = viewModel.sites.map { it.label },
                        selected = viewModel.sites.indexOfFirst { it.id == providerId },
                        onSelect = { index -> viewModel.selectSite(viewModel.sites[index].id) },
                        compact = true,
                    )
                }
            },
            onFollowBoard = { board, checked ->
                viewModel.setFollowed(board.id, checked, board.site ?: providerId)
            },
            onFavoriteBoard = { board, checked -> viewModel.setFavorite(board.id, checked, board.site ?: providerId) },
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
            onOpenLibrary = onOpenLibrary,
            watchedBadge = watchedBadge,
            hideRailOnScroll = hideRailOnScroll,
            onChromeVisibleChange = onChromeVisibleChange,
            emptyMessage =
                when (val state = uiState) {
                    HomeUiState.Loading -> stringResource(R.string.board_gallery_loading)
                    is HomeUiState.Error -> state.message
                    is HomeUiState.Success ->
                        if (state.boards.isEmpty()) {
                            stringResource(R.string.board_gallery_empty)
                        } else {
                            stringResource(R.string.board_gallery_filtered)
                        }
                },
            onRetry = if (uiState is HomeUiState.Error) viewModel::load else null,
            modifier = modifier,
        )
        if (linkDialog) {
            OpenThreadLinkDialog(
                onDismiss = { linkDialog = false },
                onOpen = { text ->
                    val link = parseThreadLink(text, viewModel.threadLinkProviders)
                    if (link != null && onOpenThread != null) {
                        onOpenThread(link.key.provider.value, link.key.board.value, link.key.thread.value, "")
                        true
                    } else {
                        false
                    }
                },
            )
        }
    }
}
