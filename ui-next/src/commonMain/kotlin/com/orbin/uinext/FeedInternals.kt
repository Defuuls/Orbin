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

internal const val GRID_TILE_ASPECT = 1.1f
internal val GRID_MIN_CELL = 170.dp
internal val IMAGE_MIN_CELL = 112.dp

/** Adaptive min cell width for feed/media size slider — mild (dense) → wild (near full-bleed). */
internal const val MEDIA_SIZE_MIN_DP = 96f
internal const val MEDIA_SIZE_MAX_DP = 400f
internal const val MEDIA_SIZE_STEPS = 11

internal val GRID_TILE_RADIUS = 14.dp
internal val GRID_CELL_PADDING = 6.dp

/** Horizontal inset for the text under a grid card's media, so it never sits on the card edge. */
internal val GRID_TEXT_INSET = 10.dp

internal fun LazyGridScope.fullWidthItem(content: @Composable () -> Unit) =
    item(key = FEED_HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) { content() }

internal fun gridPadding(
    bottom: PaddingValues,
    top: androidx.compose.ui.unit.Dp = 0.dp,
) = PaddingValues(
    start = GRID_SIDE_INSET,
    end = GRID_SIDE_INSET,
    top = top,
    bottom = bottom.calculateBottomPadding(),
)

internal val GRID_SIDE_INSET = 16.dp
internal const val FEED_HEADER_KEY = "header"
