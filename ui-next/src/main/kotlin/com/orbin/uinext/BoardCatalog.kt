package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BoardScreen(
    board: String,
    description: String,
    itemCount: Int,
    rowAt: (Int) -> FeedRow?,
    modifier: Modifier = Modifier,
    layout: FeedLayout = FeedLayout.GRID,
    onLayoutChange: (FeedLayout) -> Unit = {},
    sortLabel: String? = null,
    onSort: () -> Unit = {},
    showRail: Boolean = true,
    onOpenRow: (FeedRow) -> Unit = {},
    onSearch: () -> Unit = {},
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)? = null,
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val effectiveLayout = if (layout == FeedLayout.IMAGES) FeedLayout.IMAGES else FeedLayout.GRID
    val gridState = rememberLazyGridState()
    val railVisible =
        if (!hideRailOnScroll) {
            true
        } else {
            scrollingUp({ gridState.firstVisibleItemIndex }, { gridState.firstVisibleItemScrollOffset })
        }
    LaunchedEffect(railVisible) { onChromeVisibleChange(railVisible) }
    NextScaffold(
        where = board.takeIf { showRail },
        modifier = modifier,
        detail = stringResource(R.string.next_rail_catalog),
        onSearch = onSearch,
        railVisible = railVisible,
    ) { bottomPad ->
        val header: @Composable () -> Unit = {
            Column {
                Row(
                    modifier = Modifier.padding(start = GUTTER, top = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoardDot(board, size = 10.dp)
                    WidthSpacer(10)
                    Text(
                        text = board,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.9).sp,
                        color = next.ink,
                    )
                }
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = next.muted,
                    modifier = Modifier.padding(start = GUTTER, top = 6.dp),
                )
                Gap(16)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectableGroup()
                            .padding(horizontal = GUTTER - 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InlineAction(
                        label = stringResource(R.string.next_layout_grid),
                        selected = effectiveLayout == FeedLayout.GRID,
                        onClick = { onLayoutChange(FeedLayout.GRID) },
                    )
                    WidthSpacer(4)
                    InlineAction(
                        label = stringResource(R.string.next_layout_images),
                        selected = effectiveLayout == FeedLayout.IMAGES,
                        onClick = { onLayoutChange(FeedLayout.IMAGES) },
                    )
                    Box(modifier = Modifier.weight(1f))
                    if (sortLabel != null) {
                        InlineAction("$sortLabel ▾", onClick = onSort)
                    }
                }
                Gap(12)
                Hairline()
            }
        }
        val insets = Modifier.fillMaxSize().contentInsets()
        if (effectiveLayout == FeedLayout.IMAGES) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(IMAGE_MIN_CELL),
                state = gridState,
                modifier = insets,
                contentPadding = gridPadding(bottomPad),
            ) {
                fullWidthItem { header() }
                items(itemCount) { index ->
                    rowAt(index)?.takeIf { it.hasPreview }?.let { row ->
                        FeedImageCell(row, seed = index, onClick = onOpenRow, thumbnail = thumbnail)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(GRID_MIN_CELL),
                state = gridState,
                modifier = insets,
                contentPadding = gridPadding(bottomPad),
            ) {
                fullWidthItem { header() }
                items(itemCount) { index ->
                    rowAt(index)?.let { row ->
                        FeedGridCell(row, seed = index, onClick = onOpenRow, thumbnail = thumbnail)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PendingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PendingBar(width = 54.dp, height = 11.dp)
            Gap(9)
            PendingBar(width = 240.dp, height = 15.dp)
            Gap(8)
            PendingBar(width = 120.dp, height = 11.dp)
        }
    }
}

@Composable
internal fun PendingBar(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier =
            Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(3.dp))
                .background(next.hairline),
    )
}
