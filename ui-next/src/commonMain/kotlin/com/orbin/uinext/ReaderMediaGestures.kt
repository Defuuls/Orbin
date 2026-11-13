package com.orbin.uinext

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** A deliberate sideways drag dismisses media; paging, zooming and controls keep their gestures. */
fun Modifier.readerMediaSwipeToClose(
    onClose: () -> Unit,
    excludedTop: Dp = 0.dp,
    excludedBottom: Dp = 0.dp,
): Modifier =
    composed {
        val close by rememberUpdatedState(onClose)
        val threshold = with(LocalDensity.current) { 96.dp.toPx() }
        val edge = with(LocalDensity.current) { 48.dp.toPx() }
        val top = with(LocalDensity.current) { excludedTop.toPx() }
        val bottom = with(LocalDensity.current) { excludedBottom.toPx() }
        val insets = MediaCloseGestureInsets(edge, top, bottom)
        pointerInput(threshold, edge, top, bottom) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // System back gestures retain the edges on both platforms.
                if (!insets.contains(down.position, size)) return@awaitEachGesture
                var horizontal = 0f
                var vertical = 0f
                var horizontalDrag = false
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } > 1) break
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val delta = change.positionChange()
                    // Zoomed images and scrubbers consume movement before their parent sees it.
                    if (!horizontalDrag && change.isConsumedChildDrag()) break
                    horizontal += delta.x
                    vertical += delta.y
                    if (!horizontalDrag && maxOf(abs(horizontal), abs(vertical)) > viewConfiguration.touchSlop) {
                        if (abs(horizontal) <= abs(vertical) * MEDIA_CLOSE_AXIS_DOMINANCE) break
                        horizontalDrag = true
                    }
                    if (horizontalDrag && delta != Offset.Zero) change.consume()
                    if (!change.pressed) {
                        if (horizontalDrag && mediaCloseSwipe(horizontal, vertical, threshold)) close()
                        break
                    }
                }
            }
        }
    }

private data class MediaCloseGestureInsets(
    val edge: Float,
    val top: Float,
    val bottom: Float,
) {
    fun contains(
        position: Offset,
        size: IntSize,
    ): Boolean {
        val horizontal = position.x >= edge && position.x <= size.width - edge
        val vertical = position.y >= top && position.y <= size.height - bottom
        return horizontal && vertical
    }
}

private fun PointerInputChange.isConsumedChildDrag(): Boolean = isConsumed && position != previousPosition

internal fun mediaCloseSwipe(
    horizontal: Float,
    vertical: Float,
    threshold: Float,
): Boolean = abs(horizontal) >= threshold && abs(horizontal) > abs(vertical) * MEDIA_CLOSE_AXIS_DOMINANCE

/** Deliberately bounded choices shared by Media3, AVPlayer and WebKit. */
internal val mediaPlaybackSpeeds: List<Float> = listOf(MEDIA_NORMAL_RATE, MEDIA_FAST_RATE, MEDIA_DOUBLE_RATE)

fun mediaPlaybackSpeed(requested: Float): Float = requested.takeIf { it in mediaPlaybackSpeeds } ?: MEDIA_NORMAL_RATE

fun mediaPlaybackSpeedLabel(requested: Float): String =
    when (mediaPlaybackSpeed(requested)) {
        MEDIA_FAST_RATE -> "1.25×"
        MEDIA_DOUBLE_RATE -> "2×"
        else -> "1×"
    }

private const val MEDIA_CLOSE_AXIS_DOMINANCE = 1.25f
private const val MEDIA_NORMAL_RATE = 1f
private const val MEDIA_FAST_RATE = 1.25f
private const val MEDIA_DOUBLE_RATE = 2f
