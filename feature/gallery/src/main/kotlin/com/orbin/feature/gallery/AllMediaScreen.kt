package com.orbin.feature.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.designsystem.component.ModernSmallTopAppBar
import com.orbin.core.model.ThumbnailSize
import com.orbin.core.ui.scrollbar.FastScrollbar
import com.orbin.core.ui.scrollbar.ScrollbarDefaults
import com.orbin.core.ui.state.EmptyView
import com.orbin.core.ui.state.LoadingView
import com.orbin.media.image.MediaThumbnail

/**
 * The whole provider's media as one wall: every file on every thread of every board, in a single
 * grid that just keeps going.
 *
 * The grid fills board by board while the sweep runs, so there is something to look at within a
 * second or two rather than a spinner until all seventy boards are in. Because it runs to thousands
 * of tiles, the fast scrollbar is not optional furniture here — flinging would never get anywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllMediaScreen(
    onBack: () -> Unit,
    onOpenMedia: (provider: String, board: String, thread: Long, attachmentId: String) -> Unit,
    viewModel: AllMediaViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    AllMediaContent(
        uiState = uiState,
        isRefreshing = isRefreshing,
        thumbnailSize = settings.thumbnailSize,
        deepMediaScan = settings.deepMediaScan,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onToggleDeepScan = { viewModel.setDeepScan(!settings.deepMediaScan) },
        onOpenMedia = onOpenMedia,
    )
}

/**
 * The wall's rendering, detached from its view model so the sweep's stages — initial load, filling,
 * deep scan, complete and empty — can each be composed against fixed state in a screenshot test.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AllMediaContent(
    uiState: AllMediaUiState,
    isRefreshing: Boolean,
    thumbnailSize: ThumbnailSize,
    deepMediaScan: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleDeepScan: () -> Unit,
    onOpenMedia: (provider: String, board: String, thread: Long, attachmentId: String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()

    Scaffold(
        topBar = {
            ModernSmallTopAppBar(
                title = stringResource(R.string.all_media_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
                actions = {
                    // Mirrors the Settings switch and writes the same preference. It belongs here
                    // too because this is the only screen the deep scan affects, and the reader
                    // deciding whether the wall is deep enough is looking at the wall.
                    IconButton(onClick = onToggleDeepScan) {
                        Icon(
                            imageVector = Icons.Filled.TravelExplore,
                            contentDescription = stringResource(R.string.all_media_deep_scan),
                            tint =
                                if (deepMediaScan) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SweepProgress(uiState)

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    onRefresh()
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.isInitialLoad -> LoadingView()
                    uiState.items.isEmpty() ->
                        EmptyView(
                            if (uiState.boardsTotal == 0) {
                                stringResource(R.string.all_media_no_boards)
                            } else {
                                stringResource(R.string.all_media_empty)
                            },
                        )
                    else ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = thumbnailSize.wallColumns(),
                                contentPadding =
                                    PaddingValues(
                                        start = 4.dp,
                                        top = 4.dp,
                                        bottom = 4.dp,
                                        // Room for the scrollbar, so the last column is not sitting
                                        // underneath it.
                                        end = ScrollbarDefaults.Width,
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(uiState.items, key = { it.id }) { item ->
                                    MediaTile(
                                        item = item,
                                        onClick = {
                                            onOpenMedia(
                                                item.key.provider.value,
                                                item.key.board.value,
                                                item.key.thread.value,
                                                item.attachment.id,
                                            )
                                        },
                                    )
                                }
                            }
                            FastScrollbar(
                                gridState = gridState,
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                        }
                }
            }
        }
    }
}

/**
 * How far the sweep has got, and how much it has found. Shown while boards are still coming in and
 * then replaced by a plain count, so a reader can tell "still filling" from "that is everything".
 */
@Composable
private fun SweepProgress(uiState: AllMediaUiState) {
    if (uiState.boardsTotal == 0) return

    val summary =
        when {
            uiState.isScanning ->
                stringResource(
                    R.string.all_media_scanning,
                    uiState.boardsScanned,
                    uiState.boardsTotal,
                    uiState.items.size,
                )
            // The deep scan runs for hours, so it reports threads walked rather than pretending to
            // be a load that is about to finish.
            uiState.isDeepScanning ->
                stringResource(
                    R.string.all_media_deep_scanning,
                    uiState.threadsScanned,
                    uiState.threadsTotal,
                    uiState.items.size,
                )
            else ->
                pluralStringResource(
                    R.plurals.all_media_complete,
                    uiState.items.size,
                    uiState.items.size,
                    uiState.boardsTotal,
                )
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = summary,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        // A sweep that skipped boards is a partial wall, and the count above cannot say so — it
        // reads the same whether every board answered or a third of them failed.
        if (uiState.failedBoards > 0) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.all_media_boards_failed,
                        uiState.failedBoards,
                        uiState.failedBoards,
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 4.dp),
            )
        }
        if (uiState.isScanning) {
            LinearProgressIndicator(
                progress = {
                    uiState.boardsScanned.toFloat() / uiState.boardsTotal.coerceAtLeast(1)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (uiState.isDeepScanning) {
            LinearProgressIndicator(
                progress = {
                    uiState.threadsScanned.toFloat() / uiState.threadsTotal.coerceAtLeast(1)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One file on the wall, labelled with the board it came from so the flood stays navigable. */
@Composable
private fun MediaTile(
    item: AllMediaItem,
    onClick: () -> Unit,
) {
    Box {
        MediaThumbnail(
            attachment = item.attachment,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            onClick = onClick,
        )
        // Only the board, not the thread title: at this tile size a title is unreadable, and the
        // board is what tells a reader where an image in a wall of thousands actually came from.
        //
        // Top, not bottom: a thumbnail that fails to load draws "Image unavailable" across the
        // bottom of the tile, and a bottom-start badge landed on top of it — two overlapping runs
        // of white text, neither readable. Failures are routine here, since the sweep asks every
        // board for thumbnails at once and can be rate limited.
        Text(
            text = stringResource(R.string.all_media_board_badge, item.boardTitle),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = BADGE_ALPHA), MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/**
 * Column sizing for the wall. "Fill" means one tile per row here — the same thing it means in the
 * thread grid — and everything else packs in as many columns as the chosen thumbnail size allows.
 */
private fun ThumbnailSize.wallColumns(): GridCells =
    if (this == ThumbnailSize.FILL) {
        GridCells.Fixed(1)
    } else {
        GridCells.Adaptive(minSize = sizeDp.dp)
    }

private const val BADGE_ALPHA = 0.55f
