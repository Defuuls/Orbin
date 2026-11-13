package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_thread_fast_scroll
import com.orbin.uinext.tokens.ReaderSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

object ReaderFastScrollTags {
    const val THUMB = "reader-thread-fast-scroll"
}

/**
 * A long-list thumb shared by Android and iOS. Only a vertical drag starting on the visible
 * thumb is claimed; the frame observes ordinary post taps and list scrolling without consuming
 * them. Scroll requests share one conflated worker rather than starting a coroutine per frame.
 */
@Composable
internal fun ReaderFastScrollThumb(
    state: LazyListState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val geometry = remember(state) { FastScrollGeometry() }
    val requests = remember(state) { Channel<Int>(Channel.CONFLATED) }
    DisposableEffect(requests) { onDispose { requests.close() } }
    LaunchedEffect(state, requests) {
        for (requested in requests) {
            val count = state.layoutInfo.totalItemsCount
            if (count == 0) continue
            val index = requested.coerceIn(0, count - 1)
            val atStart = index == 0 && !state.canScrollBackward
            val atEnd = index == count - 1 && !state.canScrollForward
            val atItem = index == state.firstVisibleItemIndex && state.firstVisibleItemScrollOffset == 0
            if (!atStart && !atEnd && !atItem) {
                try {
                    state.scrollToItem(index)
                } catch (_: CancellationException) {
                    // A user scroll may cancel this jump; keep the worker for the next gesture.
                    currentCoroutineContext().ensureActive()
                }
            }
        }
    }
    Box(
        modifier
            .onGloballyPositioned {
                geometry.container = it
                geometry.updateBounds()
            }.pointerInput(state, enabled) {
                if (enabled) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture { dragThumb(state, geometry, requests, slop) }
                }
            },
    ) {
        content()
        if (enabled) FastScrollHandle(state, geometry, requests)
    }
}

@Composable
private fun BoxScope.FastScrollHandle(
    state: LazyListState,
    geometry: FastScrollGeometry,
    requests: Channel<Int>,
) {
    val visible by remember(state) {
        derivedStateOf {
            state.layoutInfo.totalItemsCount >= LONG_LIST_MIN_ITEMS &&
                (state.canScrollBackward || state.canScrollForward)
        }
    }
    if (!visible) return
    DisposableEffect(geometry) {
        onDispose {
            geometry.thumb = null
            geometry.bounds = null
        }
    }
    val targetSize = ReaderSize.touchTarget
    val targetPx = with(LocalDensity.current) { targetSize.toPx() }
    val description = stringResource(Res.string.reader_thread_fast_scroll)
    val colors = reader
    Box(
        Modifier
            .matchParentSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(top = 8.dp, bottom = 8.dp, end = 4.dp)
            .onSizeChanged { geometry.travelPx = (it.height - targetPx).coerceAtLeast(0f) },
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (scrollFraction(state) * geometry.travelPx).roundToInt()) }
                .size(targetSize)
                .testTag(ReaderFastScrollTags.THUMB)
                .onGloballyPositioned {
                    geometry.thumb = it
                    geometry.updateBounds()
                }.semantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(scrollFraction(state), 0f..1f)
                    setProgress { fraction ->
                        val count = state.layoutInfo.totalItemsCount
                        fraction.isFinite() && requests.trySend(scrollIndex(fraction, count)).isSuccess
                    }
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxHeight()
                    .width(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.accent),
            )
        }
    }
}

private class FastScrollGeometry {
    var container: LayoutCoordinates? = null
    var thumb: LayoutCoordinates? = null
    var bounds: Rect? = null
    var travelPx by mutableFloatStateOf(0f)

    fun updateBounds() {
        val parent = container
        val handle = thumb
        bounds =
            if (parent?.isAttached == true && handle?.isAttached == true) {
                parent.localBoundingBoxOf(handle, clipBounds = false)
            } else {
                null
            }
    }
}

private suspend fun AwaitPointerEventScope.dragThumb(
    state: LazyListState,
    geometry: FastScrollGeometry,
    requests: Channel<Int>,
    slop: Float,
) {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val bounds = geometry.bounds ?: return
    if (down.isConsumed || !bounds.contains(down.position) || geometry.travelPx <= 0f) return
    val start = scrollFraction(state)
    var dragging = false
    var lastIndex = -1
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) {
            if (dragging) change.consume()
            break
        }
        val vertical = change.position.y - down.position.y
        if (!dragging) {
            if (change.isConsumed) break
            val horizontal = abs(change.position.x - down.position.x)
            if (horizontal > slop && horizontal > abs(vertical)) break
            dragging = abs(vertical) > slop
        }
        if (dragging) {
            change.consume()
            val fraction = start + vertical / geometry.travelPx
            val index = scrollIndex(fraction, state.layoutInfo.totalItemsCount)
            if (index != lastIndex) {
                requests.trySend(index)
                lastIndex = index
            }
        }
    }
}

/** Includes the real header and footer items, so endpoints reach the complete list. */
private fun scrollIndex(
    fraction: Float,
    count: Int,
): Int = (fraction.coerceIn(0f, 1f) * (count - 1).coerceAtLeast(0)).roundToInt()

private fun scrollFraction(state: LazyListState): Float {
    if (!state.canScrollBackward) return 0f
    if (!state.canScrollForward) return 1f
    val info = state.layoutInfo
    val firstItem = info.visibleItemsInfo.firstOrNull { it.index == state.firstVisibleItemIndex }
    val firstSize = firstItem?.size?.coerceAtLeast(1) ?: 1
    val partial = (state.firstVisibleItemScrollOffset.toFloat() / firstSize).coerceIn(0f, 1f)
    val remaining = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(1)
    return ((state.firstVisibleItemIndex + partial) / remaining).coerceIn(0f, 1f)
}

private const val LONG_LIST_MIN_ITEMS = 40
