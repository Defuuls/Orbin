package com.orbin.core.ui.scrollbar

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.orbin.core.ui.R
import kotlinx.coroutines.launch

/**
 * A draggable scrollbar for a [LazyListState], for crossing a long list in one gesture.
 *
 * A fling scrolls by however much momentum it happens to carry, which is the wrong tool for a
 * thread with hundreds of replies: getting to the middle means flinging repeatedly and overshooting.
 * Pressing the track jumps straight there, and dragging the thumb sweeps the whole list under the
 * finger.
 *
 * Positioning is by item index rather than pixels. A lazy list only knows the height of what it has
 * measured, so a pixel-accurate bar would need every item's height up front — exactly what a lazy
 * list exists to avoid. Index-proportional means a list of uneven items tracks slightly unevenly,
 * which is a fair trade for not measuring the whole thread to draw a bar.
 */
@Composable
fun FastScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    width: Dp = ScrollbarDefaults.Width,
    minThumbHeight: Dp = ScrollbarDefaults.MinThumbHeight,
) {
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo.size
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Height of the track, filled in on layout. Kept in state so the drag maths below can convert a
    // finger position into an item index without re-measuring.
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    val minThumbPx = with(density) { minThumbHeight.toPx() }

    val metrics =
        scrollbarMetrics(
            totalItems = totalItems,
            visibleItems = visibleItems,
            firstVisibleIndex = listState.firstVisibleItemIndex,
            trackPx = trackHeightPx,
            minThumbPx = minThumbPx,
        )

    val label = stringResource(R.string.ui_fast_scrollbar)

    fun scrollToPress(yPx: Float) {
        val target =
            targetIndexForThumbCentre(
                centreYPx = yPx,
                trackPx = trackHeightPx,
                thumbPx = metrics?.thumbPx ?: minThumbPx,
                totalItems = totalItems,
                visibleItems = visibleItems,
            )
        scope.launch { listState.scrollToItem(target) }
    }

    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .width(width)
                .onSizeChanged { trackHeightPx = it.height.toFloat() }
                // Two detectors rather than one: a drag needs touch slop before it reports, so a
                // plain press would otherwise do nothing at all.
                .pointerInput(totalItems, visibleItems, trackHeightPx) {
                    detectTapGestures { offset -> scrollToPress(offset.y) }
                }.pointerInput(totalItems, visibleItems, trackHeightPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset -> scrollToPress(offset.y) },
                        onVerticalDrag = { change, _ ->
                            scrollToPress(change.position.y)
                        },
                    )
                }.semantics { contentDescription = label },
        contentAlignment = Alignment.TopCenter,
    ) {
        // Absent rather than disabled when there is nothing to scroll: a bar on a list that fits
        // the screen is decoration that still eats touches near the edge.
        if (metrics != null) {
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = ScrollbarDefaults.ThumbInset)
                        .offset { IntOffset(0, metrics.thumbOffsetPx.toInt()) }
                        .size(
                            width = width - ScrollbarDefaults.ThumbInset * 2,
                            height = with(density) { metrics.thumbPx.toDp() },
                        ).clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = ScrollbarDefaults.THUMB_ALPHA)),
            )
        }
    }
}

object ScrollbarDefaults {
    val Width: Dp = 24.dp
    val MinThumbHeight: Dp = 48.dp
    internal val ThumbInset: Dp = 8.dp
    internal const val THUMB_ALPHA = 0.55f
}

/** Where the thumb sits and how big it is, or null when the list is not worth a scrollbar. */
internal data class ScrollbarMetrics(
    val thumbPx: Float,
    val thumbOffsetPx: Float,
)

/**
 * The thumb's size and position, or null when everything already fits (nothing to scroll) or the
 * track has not been measured yet.
 *
 * The thumb is sized by the fraction of the list on screen, then floored at [minThumbPx] so a very
 * long thread still leaves something big enough to grab.
 */
internal fun scrollbarMetrics(
    totalItems: Int,
    visibleItems: Int,
    firstVisibleIndex: Int,
    trackPx: Float,
    minThumbPx: Float,
): ScrollbarMetrics? {
    val scrollableItems = totalItems - visibleItems
    if (trackPx <= 0f || totalItems <= 0 || scrollableItems <= 0) return null

    val thumbPx = (trackPx * visibleItems / totalItems).coerceIn(minThumbPx.coerceAtMost(trackPx), trackPx)
    val travelPx = trackPx - thumbPx
    val progress = (firstVisibleIndex.toFloat() / scrollableItems).coerceIn(0f, 1f)
    return ScrollbarMetrics(thumbPx = thumbPx, thumbOffsetPx = travelPx * progress)
}

/**
 * The item index to scroll to when the finger is at [centreYPx] down the track.
 *
 * The press is treated as the thumb's centre rather than its top, so the list lands where the
 * finger is pointing instead of half a thumb below it.
 */
internal fun targetIndexForThumbCentre(
    centreYPx: Float,
    trackPx: Float,
    thumbPx: Float,
    totalItems: Int,
    visibleItems: Int,
): Int {
    val scrollableItems = totalItems - visibleItems
    if (scrollableItems <= 0) return 0
    val travelPx = (trackPx - thumbPx).coerceAtLeast(1f)
    val progress = ((centreYPx - thumbPx / 2f) / travelPx).coerceIn(0f, 1f)
    return (progress * scrollableItems).toInt().coerceIn(0, scrollableItems)
}
