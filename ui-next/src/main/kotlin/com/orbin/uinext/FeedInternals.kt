package com.orbin.uinext

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp

@Composable
internal fun scrollingUp(
    firstVisibleItemIndex: () -> Int,
    firstVisibleItemScrollOffset: () -> Int,
): Boolean {
    var up by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        var lastIndex = firstVisibleItemIndex()
        var lastOffset = firstVisibleItemScrollOffset()
        snapshotFlow { firstVisibleItemIndex() to firstVisibleItemScrollOffset() }
            .collect { (index, offset) ->
                up =
                    when {
                        index == 0 && offset == 0 -> true
                        lastIndex != index -> lastIndex > index
                        else -> lastOffset >= offset
                    }
                lastIndex = index
                lastOffset = offset
            }
    }
    return up
}

internal val LIST_TILE_WIDTH = 88.dp
internal val LIST_TILE_HEIGHT = 68.dp
internal const val GRID_TILE_ASPECT = 1.1f
internal val GRID_MIN_CELL = 170.dp
internal val IMAGE_MIN_CELL = 112.dp
internal val GRID_TILE_RADIUS = 16.dp
internal val GRID_CELL_PADDING = 8.dp

internal fun LazyGridScope.fullWidthItem(content: @Composable () -> Unit) =
    item(key = FEED_HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) { content() }

internal fun gridPadding(bottom: PaddingValues) =
    PaddingValues(
        start = GRID_SIDE_INSET,
        end = GRID_SIDE_INSET,
        bottom = bottom.calculateBottomPadding(),
    )

internal val GRID_SIDE_INSET = 14.dp
internal const val FEED_HEADER_KEY = "header"
