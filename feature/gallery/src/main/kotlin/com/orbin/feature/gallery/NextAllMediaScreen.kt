package com.orbin.feature.gallery

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.media.image.MediaThumbnail
import com.orbin.uinext.MediaCell
import com.orbin.uinext.MediaWallScreen
import com.orbin.uinext.MessageScreen
import com.orbin.uinext.NextTheme

/**
 * The redesigned media wall, wired to the same [AllMediaViewModel] the current one uses.
 *
 * The sweep's progress is the only thing the screen reports, and it reports it only while
 * something is happening: a determinate bar that never leaves is decoration. Failed boards keep
 * their line, because a wall quietly missing a board's files is worse than one that says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextAllMediaScreen(
    onOpenMedia: (provider: String, board: String, thread: Long, attachmentId: String) -> Unit,
    onOpenCommands: () -> Unit,
    modifier: Modifier = Modifier,
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    viewModel: AllMediaViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    NextAllMediaContent(
        uiState = uiState,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        onOpenMedia = onOpenMedia,
        onOpenCommands = onOpenCommands,
        modifier = modifier,
        hideRailOnScroll = hideRailOnScroll,
        onChromeVisibleChange = onChromeVisibleChange,
    )
}

/**
 * The wall's rendering, detached from its view model so the sweep's stages — initial load, filling,
 * deep scan, complete, partial and empty — can each be composed against fixed state in a screenshot
 * test. The same split the previous wall used, and for the same reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextAllMediaContent(
    uiState: AllMediaUiState,
    isRefreshing: Boolean,
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    onRefresh: () -> Unit,
    onOpenMedia: (provider: String, board: String, thread: Long, attachmentId: String) -> Unit,
    onOpenCommands: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cells = remember(uiState.items) { uiState.items.map { it.toCell() } }
    val byId = remember(uiState.items) { uiState.items.associateBy { it.id } }

    NextTheme {
        // Nothing swept yet and nothing to show: the sweep itself is the content, so the progress
        // line has no grid to sit above.
        if (uiState.isInitialLoad) {
            MessageScreen(
                title = stringResource(R.string.next_media_title),
                subtitle = stringResource(R.string.next_media_sweeping, uiState.boardsTotal),
                modifier = modifier,
            )
            return@NextTheme
        }
        if (cells.isEmpty()) {
            MessageScreen(
                title = stringResource(R.string.next_media_title),
                subtitle = stringResource(R.string.next_media_empty),
                actionLabel = stringResource(R.string.next_media_rescan),
                onAction = onRefresh,
                modifier = modifier,
            )
            return@NextTheme
        }
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier.fillMaxSize(),
        ) {
            MediaWallScreen(
                scanned = uiState.boardsScanned,
                total = uiState.boardsTotal,
                failed = uiState.failedBoards,
                cells = cells,
                scanning = uiState.isScanning,
                deepScanning = uiState.isDeepScanning,
                deepScanned = uiState.threadsScanned,
                deepTotal = uiState.threadsTotal,
                hideRailOnScroll = hideRailOnScroll,
                onChromeVisibleChange = onChromeVisibleChange,
                onSearch = onOpenCommands,
                onOpen = { cell ->
                    byId[cell.id]?.let { item ->
                        onOpenMedia(
                            item.key.provider.value,
                            item.key.board.value,
                            item.key.thread.value,
                            item.attachment.id,
                        )
                    }
                },
                tile = { cell, tileModifier ->
                    byId[cell.id]?.let { item ->
                        MediaThumbnail(
                            attachment = item.attachment,
                            modifier = tileModifier.clip(RoundedCornerShape(10.dp)),
                        )
                    }
                },
            )
        }
    }
}

private fun AllMediaItem.toCell() = MediaCell(id = id, board = "/${key.board.value}/")
