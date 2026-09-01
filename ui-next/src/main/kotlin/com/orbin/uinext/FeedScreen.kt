package com.orbin.uinext

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

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
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)? = null,
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    scrollToTopRequest: Int = 0,
) {
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
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
    NextScaffold(
        where = stringResource(R.string.next_feed_title).takeIf { showRail },
        modifier = modifier,
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
                        )
                        if (index < rows.lastIndex) Hairline(inset = true)
                    }
                }

            FeedLayout.GRID ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(GRID_MIN_CELL),
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
                        )
                    }
                }

            FeedLayout.IMAGES ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(IMAGE_MIN_CELL),
                    state = gridState,
                    modifier = insets,
                    contentPadding = gridPadding(bottomPad),
                ) {
                    fullWidthItem { header() }
                    itemsIndexed(withPreview, key = { _, row -> row.id }) { index, row ->
                        FeedImageCell(row, seed = index, onClick = onOpenRow, thumbnail = thumbnail)
                    }
                }
        }
    }
}
