package com.orbin.feature.gallery

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.orbin.uinext.NextPullToRefresh
import com.orbin.uinext.NextTheme

/**
 * The redesigned media wall, wired to the same [AllMediaViewModel] the current one uses.
 *
 * The sweep's progress is the only thing the screen reports, and it reports it only while
 * something is happening: a determinate bar that never leaves is decoration. Failed boards keep
 * their line, because a wall quietly missing a board's files is worse than one that says so.
 */
@Composable
fun NextAllMediaScreen(
    onOpenMedia: (provider: String, board: String, thread: Long, attachmentId: String) -> Unit,
    modifier: Modifier = Modifier,
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    onOpenFeed: (() -> Unit)? = null,
    onOpenBoards: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    title: String? = null,
    onOpenSaved: (() -> Unit)? = null,
    viewModel: AllMediaViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    NextAllMediaContent(
        uiState = uiState,
        isRefreshing = isRefreshing,
        showSizeControl = true,
        onRefresh = viewModel::refresh,
        onOpenMedia = onOpenMedia,
        onSave = viewModel::save,
        title = title,
        onOpenSaved = onOpenSaved,
        modifier = modifier,
        hideRailOnScroll = hideRailOnScroll,
        onChromeVisibleChange = onChromeVisibleChange,
        onOpenFeed = onOpenFeed,
        onOpenBoards = onOpenBoards,
        onOpenSettings = onOpenSettings,
    )
}

/**
 * The wall's rendering, detached from its view model so the sweep's stages — initial load, filling,
 * deep scan, complete, partial and empty — can each be composed against fixed state in a screenshot
 * test. The same split the previous wall used, and for the same reason.
 */
@Composable
fun NextAllMediaContent(
    uiState: AllMediaUiState,
    isRefreshing: Boolean,
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    showSizeControl: Boolean = false,
    onRefresh: () -> Unit,
    onOpenMedia: (provider: String, board: String, thread: Long, attachmentId: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenFeed: (() -> Unit)? = null,
    onOpenBoards: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onSave: ((AllMediaItem) -> Unit)? = null,
    title: String? = null,
    onOpenSaved: (() -> Unit)? = null,
) {
    val wallTitle = title ?: stringResource(R.string.next_media_title)
    val cells = remember(uiState.items) { uiState.items.map { it.toCell() } }
    var actionsFor by remember { mutableStateOf<AllMediaItem?>(null) }
    val byId = remember(uiState.items) { uiState.items.associateBy { it.id } }

    NextTheme {
        // Nothing swept yet and nothing to show: the sweep itself is the content, so the progress
        // line has no grid to sit above.
        val hasTabs = onOpenFeed != null || onOpenBoards != null || onOpenSettings != null
        val onDestination: ((com.orbin.uinext.NextDestination) -> Unit)? =
            if (hasTabs) {
                { dest ->
                    when (dest) {
                        com.orbin.uinext.NextDestination.FEED -> onOpenFeed?.invoke()
                        com.orbin.uinext.NextDestination.BOARDS -> onOpenBoards?.invoke()
                        com.orbin.uinext.NextDestination.MEDIA -> Unit
                        com.orbin.uinext.NextDestination.SETTINGS -> onOpenSettings?.invoke()
                    }
                }
            } else {
                null
            }
        if (uiState.isInitialLoad) {
            MessageScreen(
                title = wallTitle,
                subtitle = stringResource(R.string.next_media_sweeping, uiState.boardsTotal),
                where = wallTitle,
                destination =
                    com.orbin.uinext.NextDestination.MEDIA
                        .takeIf { hasTabs },
                onDestination = onDestination,
                modifier = modifier,
            )
            return@NextTheme
        }
        if (cells.isEmpty()) {
            MessageScreen(
                title = wallTitle,
                subtitle = stringResource(R.string.next_media_empty),
                actionLabel = stringResource(R.string.next_media_rescan),
                onAction = onRefresh,
                where = wallTitle,
                destination =
                    com.orbin.uinext.NextDestination.MEDIA
                        .takeIf { hasTabs },
                onDestination = onDestination,
                modifier = modifier,
            )
            return@NextTheme
        }
        NextPullToRefresh(
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
                showSizeControl = showSizeControl,
                hideRailOnScroll = hideRailOnScroll,
                onChromeVisibleChange = onChromeVisibleChange,
                onOpenFeed = onOpenFeed,
                onOpenBoards = onOpenBoards,
                onOpenSettings = onOpenSettings,
                title = title,
                onOpenSaved = onOpenSaved,
                onLongPress = onSave?.let { { cell -> actionsFor = byId[cell.id] } },
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
        val pressed = actionsFor
        if (pressed != null && onSave != null) {
            MediaActionsSheet(
                attachment = pressed.attachment,
                onSave = { onSave(pressed) },
                onDismiss = { actionsFor = null },
            )
        }
    }
}

private fun AllMediaItem.toCell() = MediaCell(id = id, board = "/${key.board.value}/")
