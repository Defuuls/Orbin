package com.orbin.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import coil3.compose.AsyncImage
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.abs

/** What an Apple Pencil asks for with its barrel: a double-tap (2nd generation and Pro) or a squeeze (Pro). */
internal enum class PencilGesture { DOUBLE_TAP, SQUEEZE }

/**
 * Where a hovering Pencil tip is, in points from the app's top-left (the same as Compose dp at the
 * root), and the barrel's roll in radians when the Pencil reports it (Pro, iOS 17.5 and later).
 */
internal data class PencilHover(
    val x: Float,
    val y: Float,
    val roll: Float?,
)

/**
 * The Pencil's input, fed by the iOS side (`attachPencil`) and read by the screens. Nothing arrives
 * on devices or Pencils without the hardware, so every screen keeps working the same without one.
 */
internal object Pencil {
    private val gestureEvents = MutableSharedFlow<PencilGesture>(extraBufferCapacity = 8)
    private val hoverState = MutableStateFlow<PencilHover?>(null)

    val gestures: SharedFlow<PencilGesture> = gestureEvents
    val hover: StateFlow<PencilHover?> = hoverState.asStateFlow()

    fun gesture(gesture: PencilGesture) {
        gestureEvents.tryEmit(gesture)
    }

    fun hoverAt(hover: PencilHover?) {
        hoverState.value = hover
    }
}

/** Runs [onGesture] for each Pencil double-tap or squeeze while this composable is on screen. */
@Composable
internal fun OnPencilGesture(onGesture: suspend (PencilGesture) -> Unit) {
    val current by rememberUpdatedState(onGesture)
    LaunchedEffect(Unit) { Pencil.gestures.collect { current(it) } }
}

/**
 * Runs [onRoll] with each change of the barrel's roll, in radians, while the Pencil hovers. A
 * reading that jumps (the Pencil left and came back) starts afresh rather than counting as a turn.
 */
@Composable
internal fun OnPencilRoll(onRoll: suspend (Float) -> Unit) {
    val current by rememberUpdatedState(onRoll)
    LaunchedEffect(Unit) {
        var last: Float? = null
        Pencil.hover.collect { hover ->
            val roll = hover?.roll
            val previous = last
            last = roll
            if (roll == null || previous == null) return@collect
            val turn = rollDelta(previous, roll)
            if (abs(turn) < MAX_ROLL_STEP) current(turn)
        }
    }
}

/** The shortest turn from [from] to [to], across the ±π seam. */
internal fun rollDelta(
    from: Float,
    to: Float,
): Float {
    var delta = to - from
    if (delta > PI) delta -= (2 * PI).toFloat()
    if (delta < -PI) delta += (2 * PI).toFloat()
    return delta
}

/**
 * A larger look at [attachment] while the Pencil hovers over [content] for a moment, the way a
 * long press would, without touching the screen. Off the Pencil nothing changes.
 */
@Composable
internal fun PencilHoverPreview(
    attachment: MediaAttachment,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val hover by Pencil.hover.collectAsState()
    val density = LocalDensity.current.density
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val over = hover?.let { bounds.contains(Offset(it.x * density, it.y * density)) } == true
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(over) {
        if (over) delay(HOVER_PREVIEW_DELAY_MS)
        showing = over
    }
    Box(modifier.onGloballyPositioned { bounds = it.boundsInRoot() }) {
        content()
        // A spoiler stays covered: hovering must not show what a tap would still ask about.
        if (showing && !attachment.isSpoiler) {
            Popup(alignment = Alignment.Center) {
                AsyncImage(
                    // A still image previews whole; anything else has only its thumbnail.
                    model = if (attachment.type == MediaType.IMAGE) attachment.sourceUrl else attachment.thumbnailUrl,
                    contentDescription = attachment.originalFileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(HOVER_PREVIEW_SIZE).clip(RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}

/**
 * The actions a Pencil squeeze offers on the current screen, as a small menu in the middle of it.
 * Each entry is its label and what it does; picking one closes the menu.
 */
@Composable
internal fun PencilMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            actions.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onDismiss()
                        action()
                    },
                )
            }
        }
    }
}

private const val MAX_ROLL_STEP = 1f
private const val HOVER_PREVIEW_DELAY_MS = 350L
private val HOVER_PREVIEW_SIZE = 280.dp
