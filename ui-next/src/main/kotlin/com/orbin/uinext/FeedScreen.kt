package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun FeedScreen(
    rows: List<FeedRow>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    railDetail: String? = null,
    showRail: Boolean = true,
    layout: FeedLayout = FeedLayout.GRID,
    onLayoutChange: (FeedLayout) -> Unit = {},
    sortLabel: String? = null,
    onSort: () -> Unit = {},
    filter: String? = null,
    onClearFilter: () -> Unit = {},
    onOpenRow: (FeedRow) -> Unit = {},
    railAction: String = stringResource(R.string.next_action_search),
    onSearch: () -> Unit = {},
    onSettings: (() -> Unit)? = null,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)? = null,
    activityText: @Composable (FeedRow) -> String = { it.activity },
    onActivePreviewChanged: (String?) -> Unit = {},
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    scrollToTopRequest: Int = 0,
    showSizeControl: Boolean = false,
    onOpenBoards: (() -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    onOpenSearchDestination: (() -> Unit)? = null,
    onOpenMedia: (() -> Unit)? = null,
) {
    val effectiveLayout = if (layout == FeedLayout.IMAGES) FeedLayout.IMAGES else FeedLayout.GRID
    val gridState = rememberLazyGridState()
    var feedSize by rememberSaveable { mutableFloatStateOf(GRID_MIN_CELL.value) }
    val imageGridMinSize = if (showSizeControl) feedSize.dp else IMAGE_MIN_CELL
    val imageHeight = if (showSizeControl) (feedSize * FEED_IMAGE_TILE_HEIGHT_RATIO).dp else 124.dp

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) gridState.animateScrollToItem(0)
    }
    val railVisible =
        if (!hideRailOnScroll) {
            true
        } else {
            scrollingUp({ gridState.firstVisibleItemIndex }, { gridState.firstVisibleItemScrollOffset })
        }
    LaunchedEffect(railVisible) { onChromeVisibleChange(railVisible) }
    val withPreview = remember(rows) { rows.filter { it.hasPreview } }
    val omittedWithoutPreview = rows.size - withPreview.size
    val activePreviewCallback = rememberUpdatedState(onActivePreviewChanged)

    // Emit at most one on-screen preview id so callers can hard-cap feed ExoPlayers to 0–1.
    LaunchedEffect(effectiveLayout, rows, withPreview, gridState) {
        snapshotFlow {
            val candidates =
                when (effectiveLayout) {
                    FeedLayout.IMAGES ->
                        gridState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                            withPreview
                                .getOrNull(item.index - FEED_CONTENT_INDEX_OFFSET)
                                ?.takeIf { !it.muted }
                                ?.id
                        }

                    else ->
                        gridState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                            rows
                                .getOrNull(item.index - FEED_CONTENT_INDEX_OFFSET)
                                ?.takeIf { it.hasPreview && !it.muted }
                                ?.id
                        }
                }
            candidates.take(MAX_FEED_AUTOPLAY_IDS).firstOrNull()
        }.distinctUntilChanged()
            .collect { activePreviewCallback.value(it) }
    }

    DisposableEffect(Unit) {
        onDispose { activePreviewCallback.value(null) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NextScaffold(
            where = stringResource(R.string.next_feed_title).takeIf { showRail },
            modifier = Modifier.fillMaxSize(),
            detail = railDetail,
            action = railAction,
            onSearch = onSearch,
            railVisible = railVisible,
        ) { bottomPad ->
            val header: @Composable () -> Unit = {
                FeedHeader(
                    subtitle = subtitle ?: pluralStringResource(R.plurals.next_feed_thread_count, rows.size, rows.size),
                    layout = effectiveLayout,
                    onLayoutChange = onLayoutChange,
                    sortLabel = sortLabel,
                    onSort = onSort,
                    filter = filter,
                    onClearFilter = onClearFilter,
                    omittedWithoutPreview = omittedWithoutPreview,
                    sizeValue = feedSize,
                    onSizeChange = { feedSize = it.coerceIn(FEED_SIZE_MIN_DP, FEED_SIZE_MAX_DP) },
                    showSizeControl = showSizeControl,
                    onOpenBoards = onOpenBoards,
                    onOpenHistory = onOpenHistory,
                    onOpenDownloads = onOpenDownloads,
                    onOpenSearch = onOpenSearchDestination,
                    onOpenMedia = onOpenMedia,
                )
            }
            val insets = Modifier.fillMaxSize().contentInsets()
            if (effectiveLayout == FeedLayout.IMAGES) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(imageGridMinSize),
                    state = gridState,
                    modifier = insets,
                    contentPadding = gridPadding(bottomPad),
                ) {
                    fullWidthItem { header() }
                    itemsIndexed(
                        withPreview,
                        key = { _, row -> row.id },
                        contentType = { _, _ -> "feed-image-cell" },
                    ) { index, row ->
                        FeedImageCell(
                            row,
                            seed = index,
                            onClick = onOpenRow,
                            thumbnail = thumbnail,
                            tileHeight = imageHeight,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(feedSize.dp),
                    state = gridState,
                    modifier = insets,
                    contentPadding = gridPadding(bottomPad),
                ) {
                    fullWidthItem { header() }
                    itemsIndexed(
                        rows,
                        key = { _, row -> row.id },
                        contentType = { _, row -> if (row.hasPreview) "feed-grid-preview" else "feed-grid" },
                    ) { index, row ->
                        FeedGridCell(
                            row,
                            seed = index,
                            onClick = onOpenRow,
                            thumbnail = thumbnail,
                            activityText = activityText,
                        )
                    }
                }
            }
        }

        if (showRail && railVisible && onSettings != null) {
            InlineAction(
                label = stringResource(R.string.next_settings_title),
                onClick = onSettings,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                        ).padding(end = 92.dp, bottom = 14.dp),
            )
        }
    }
}

internal const val FEED_SIZE_MIN_DP = MEDIA_SIZE_MIN_DP
internal const val FEED_SIZE_MAX_DP = MEDIA_SIZE_MAX_DP
internal const val FEED_SIZE_STEPS = MEDIA_SIZE_STEPS
private const val FEED_IMAGE_TILE_HEIGHT_RATIO = 0.74f
private const val FEED_CONTENT_INDEX_OFFSET = 1

/** Matches media.video.MAX_FEED_AUTOPLAY_PLAYERS — keep feed ExoPlayer count at 0 or 1. */
private const val MAX_FEED_AUTOPLAY_IDS = 1
