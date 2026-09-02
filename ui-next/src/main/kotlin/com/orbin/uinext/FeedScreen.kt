package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
    scrollToTopRequest: Int = 0,
    showSizeControl: Boolean = false,
) {
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    var feedSize by rememberSaveable { mutableFloatStateOf(GRID_MIN_CELL.value) }
    val sizeScale = feedSize / GRID_MIN_CELL.value
    val imageHeight = (feedSize * FEED_IMAGE_TILE_HEIGHT_RATIO).dp

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) {
            if (layout == FeedLayout.LIST) {
                listState.animateScrollToItem(0)
            } else {
                gridState.animateScrollToItem(0)
            }
        }
    }
    val railVisible =
        if (!hideRailOnScroll) {
            true
        } else if (layout == FeedLayout.LIST) {
            scrollingUp({ listState.firstVisibleItemIndex }, { listState.firstVisibleItemScrollOffset })
        } else {
            scrollingUp({ gridState.firstVisibleItemIndex }, { gridState.firstVisibleItemScrollOffset })
        }
    LaunchedEffect(railVisible) { onChromeVisibleChange(railVisible) }
    val withPreview = remember(rows) { rows.filter { it.hasPreview } }
    val omittedWithoutPreview = rows.size - withPreview.size
    val activePreviewCallback = rememberUpdatedState(onActivePreviewChanged)

    LaunchedEffect(layout, rows, withPreview, listState, gridState) {
        snapshotFlow {
            when (layout) {
                FeedLayout.LIST ->
                    listState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { item ->
                        rows
                            .getOrNull(item.index - FEED_CONTENT_INDEX_OFFSET)
                            ?.takeIf { it.hasPreview && !it.muted }
                            ?.id
                    }

                FeedLayout.GRID ->
                    gridState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { item ->
                        rows
                            .getOrNull(item.index - FEED_CONTENT_INDEX_OFFSET)
                            ?.takeIf { it.hasPreview && !it.muted }
                            ?.id
                    }

                FeedLayout.IMAGES ->
                    gridState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { item ->
                        withPreview
                            .getOrNull(item.index - FEED_CONTENT_INDEX_OFFSET)
                            ?.takeIf { !it.muted }
                            ?.id
                    }
            }
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
                    layout = layout,
                    onLayoutChange = onLayoutChange,
                    sortLabel = sortLabel,
                    onSort = onSort,
                    filter = filter,
                    onClearFilter = onClearFilter,
                    omittedWithoutPreview = omittedWithoutPreview,
                    sizeValue = feedSize,
                    onSizeChange = { feedSize = it },
                    showSizeControl = showSizeControl,
                )
            }
            val insets = Modifier.fillMaxSize().contentInsets()
            when (layout) {
                FeedLayout.LIST ->
                    LazyColumn(
                        state = listState,
                        modifier = insets,
                        contentPadding = bottomPad,
                    ) {
                        item(key = FEED_HEADER_KEY) { header() }
                        itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                            FeedRowView(
                                row,
                                seed = index,
                                modifier = Modifier.animateItem(),
                                onClick = onOpenRow,
                                thumbnail = thumbnail,
                                activityText = activityText,
                                sizeScale = sizeScale,
                            )
                            if (index < rows.lastIndex) Hairline(inset = true)
                        }
                    }

                FeedLayout.GRID ->
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(feedSize.dp),
                        state = gridState,
                        modifier = insets,
                        contentPadding = gridPadding(bottomPad),
                    ) {
                        fullWidthItem { header() }
                        itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                            FeedGridCell(
                                row,
                                seed = index,
                                onClick = onOpenRow,
                                thumbnail = thumbnail,
                                modifier = Modifier.animateItem(),
                                activityText = activityText,
                            )
                        }
                    }

                FeedLayout.IMAGES ->
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(feedSize.dp),
                        state = gridState,
                        modifier = insets,
                        contentPadding = gridPadding(bottomPad),
                    ) {
                        fullWidthItem { header() }
                        itemsIndexed(withPreview, key = { _, row -> row.id }) { index, row ->
                            FeedImageCell(
                                row,
                                seed = index,
                                onClick = onOpenRow,
                                thumbnail = thumbnail,
                                tileHeight = imageHeight,
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

internal const val FEED_SIZE_MIN_DP = 96f
internal const val FEED_SIZE_MAX_DP = 240f
internal const val FEED_SIZE_STEPS = 5
private const val FEED_IMAGE_TILE_HEIGHT_RATIO = 0.74f
private const val FEED_CONTENT_INDEX_OFFSET = 1
