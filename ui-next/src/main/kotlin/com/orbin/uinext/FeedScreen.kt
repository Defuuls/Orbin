package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
    layout: FeedLayout = FeedLayout.LIST,
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
    onCompactTitleVisibleChange: (Boolean) -> Unit = {},
    scrollToTopRequest: Int = 0,
    showSizeControl: Boolean = false,
    onOpenBoards: (() -> Unit)? = null,
    onOpenMedia: (() -> Unit)? = null,
    headerContent: @Composable () -> Unit = {},
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    groupByBoard: Boolean = true,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    val effectiveLayout = layout
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
                gridState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                    rows.firstOrNull { it.id == item.key && it.hasPreview && !it.muted }?.id
                }
            candidates.take(MAX_FEED_AUTOPLAY_IDS).firstOrNull()
        }.distinctUntilChanged()
            .collect { activePreviewCallback.value(it) }
    }

    DisposableEffect(Unit) {
        onDispose { activePreviewCallback.value(null) }
    }

    val hasTabs = onOpenBoards != null || onOpenMedia != null || onSettings != null
    val onDestination: ((NextDestination) -> Unit)? =
        if (hasTabs) {
            { dest ->
                when (dest) {
                    NextDestination.FEED -> Unit
                    NextDestination.BOARDS -> onOpenBoards?.invoke()
                    NextDestination.MEDIA -> onOpenMedia?.invoke()
                    NextDestination.SETTINGS -> onSettings?.invoke()
                }
            }
        } else {
            null
        }
    val feedTitle = stringResource(R.string.next_feed_title)
    val showCompactTitle by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 ||
                gridState.firstVisibleItemScrollOffset > 64
        }
    }
    LaunchedEffect(showCompactTitle) { onCompactTitleVisibleChange(showCompactTitle) }

    Box(modifier = modifier.fillMaxSize()) {
        NextScaffold(
            where = stringResource(R.string.next_feed_title).takeIf { showRail && !hasTabs },
            modifier = Modifier.fillMaxSize(),
            detail = railDetail.takeIf { !hasTabs },
            action = railAction,
            onSearch = onSearch,
            railVisible = railVisible,
            destination = NextDestination.FEED.takeIf { showRail && hasTabs },
            onDestination = onDestination.takeIf { showRail },
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
                    showSizeControl = showSizeControl && layout != FeedLayout.LIST,
                    headerContent = headerContent,
                    query = query,
                    onQueryChange = onQueryChange,
                    refreshing = refreshing,
                    onRefresh = onRefresh,
                )
            }
            val insets = Modifier.fillMaxSize().contentInsets()
            val visibleRows = if (effectiveLayout == FeedLayout.IMAGES) withPreview else rows
            val groups =
                if (groupByBoard) {
                    visibleRows.groupBy { it.boardTitle to it.board }
                } else {
                    linkedMapOf(
                        ("" to "") to visibleRows,
                    )
                }
            LazyVerticalGrid(
                columns =
                    when (effectiveLayout) {
                        FeedLayout.LIST -> GridCells.Fixed(1)
                        FeedLayout.GRID -> if (showSizeControl) GridCells.Adaptive(feedSize.dp) else GridCells.Fixed(2)
                        FeedLayout.IMAGES ->
                            if (showSizeControl) {
                                GridCells.Adaptive(
                                    imageGridMinSize,
                                )
                            } else {
                                GridCells.Fixed(3)
                            }
                    },
                state = gridState,
                modifier = insets,
                contentPadding = gridPadding(bottomPad),
            ) {
                fullWidthItem { header() }
                if (visibleRows.isEmpty()) {
                    item(key = "feed-empty", span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(vertical = 24.dp)) {
                            ScreenTitle(
                                "Nothing here yet",
                                subtitle = "Try a different search, or follow a few more boards.",
                                size = 22,
                            )
                            onOpenBoards?.let { InlineAction("Explore boards", accent = true, onClick = it) }
                        }
                    }
                }
                groups.forEach { (board, group) ->
                    if (groupByBoard) {
                        item(key = "board:${board.second}", span = { GridItemSpan(maxLineSpan) }) {
                            FeedGroupHeading(board.first, board.second, group.size)
                        }
                    }
                    itemsIndexed(group, key = {
                        _,
                        row,
                        ->
                        row.id
                    }, contentType = { _, _ -> effectiveLayout.name }) { index, row ->
                        when (effectiveLayout) {
                            FeedLayout.LIST ->
                                FeedListRow(
                                    row,
                                    index,
                                    onOpenRow,
                                    thumbnail,
                                    activityText,
                                    index == 0,
                                    index == group.lastIndex,
                                )
                            FeedLayout.GRID ->
                                FeedGridCell(
                                    row,
                                    index,
                                    onOpenRow,
                                    thumbnail,
                                    activityText = activityText,
                                )
                            FeedLayout.IMAGES ->
                                FeedImageCell(
                                    row,
                                    index,
                                    onOpenRow,
                                    thumbnail,
                                    tileHeight = imageHeight,
                                )
                        }
                    }
                }
            }
        }
        CompactTitleBar(
            title = feedTitle,
            visible = showCompactTitle,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

internal const val FEED_SIZE_MIN_DP = MEDIA_SIZE_MIN_DP
internal const val FEED_SIZE_MAX_DP = MEDIA_SIZE_MAX_DP
internal const val FEED_SIZE_STEPS = MEDIA_SIZE_STEPS
private const val FEED_IMAGE_TILE_HEIGHT_RATIO = 0.74f

/** Matches media.video.MAX_FEED_AUTOPLAY_PLAYERS — keep feed ExoPlayer count at 0 or 1. */
private const val MAX_FEED_AUTOPLAY_IDS = 1
