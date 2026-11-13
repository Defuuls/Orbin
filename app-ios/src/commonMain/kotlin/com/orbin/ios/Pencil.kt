package com.orbin.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberConstraintsSizeResolver
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.uinext.MIN_TOUCH_TARGET
import com.orbin.uinext.OrbinCard
import com.orbin.uinext.nextClickable
import com.orbin.uinext.reader
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.withReaderFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    thumbnailOnly: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val contentDensity = LocalDensity.current
    val density = contentDensity.density
    // Where the picture is, kept out of Compose state: written on every layout pass while a list
    // scrolls, as state it recomposed every picture on screen every frame.
    val bounds = remember { BoundsHolder() }
    var showing by remember { mutableStateOf(false) }
    // Only the Pencil's hover drives this, and nothing recomposes until it is over this picture.
    LaunchedEffect(Unit) {
        Pencil.hover
            .map { point -> point != null && bounds.rect.contains(Offset(point.x * density, point.y * density)) }
            .distinctUntilChanged()
            .collectLatest { over ->
                if (over) delay(HOVER_PREVIEW_DELAY_MS)
                showing = over
            }
    }
    Box(modifier.onGloballyPositioned { bounds.rect = it.boundsInRoot() }) {
        content()
        // A spoiler stays covered: hovering must not show what a tap would still ask about.
        if (showing && !attachment.isSpoiler) {
            Popup(alignment = Alignment.Center) {
                CompositionLocalProvider(LocalDensity provides contentDensity) {
                    if (thumbnailOnly) {
                        ReaderThumbnailHover(attachment)
                    } else {
                        AsyncImage(
                            // A still image previews whole; anything else has only its thumbnail.
                            model =
                                if (attachment.type ==
                                    MediaType.IMAGE
                                ) {
                                    attachment.sourceUrl
                                } else {
                                    attachment.thumbnailUrl
                                },
                            contentDescription = attachment.originalFileName,
                            contentScale = ContentScale.Fit,
                            modifier =
                                Modifier.size(HOVER_PREVIEW_SIZE).clip(RoundedCornerShape(OrbinRadius.thumbnail)),
                        )
                    }
                }
            }
        }
    }
}

/** Catalog hover keeps the same provider-thumbnail-only contract as the square beneath it. */
@Composable
private fun ReaderThumbnailHover(attachment: MediaAttachment) {
    val context = LocalPlatformContext.current
    val sizeResolver = rememberConstraintsSizeResolver()
    val request =
        remember(context, attachment.thumbnailUrl, sizeResolver) {
            readerThumbnailRequest(context, attachment, sizeResolver)
        }
    Box(Modifier.size(HOVER_PREVIEW_SIZE).clip(RoundedCornerShape(OrbinRadius.thumbnail))) {
        AsyncImage(
            model = request,
            contentDescription = attachment.originalFileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().then(sizeResolver),
        )
    }
}

private class BoundsHolder {
    var rect: Rect = Rect.Zero
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
    val contentDensity = LocalDensity.current
    Box(modifier) {
        if (expanded) {
            Popup(
                alignment = Alignment.Center,
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true),
            ) {
                CompositionLocalProvider(LocalDensity provides contentDensity) {
                    OrbinCard(modifier = Modifier.widthIn(min = 220.dp, max = 320.dp)) {
                        actions.forEach { (label, action) ->
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = MIN_TOUCH_TARGET)
                                        .nextClickable(
                                            role = Role.Button,
                                            onClick = {
                                                onDismiss()
                                                action()
                                            },
                                        ).padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = label,
                                    style = OrbinType.readerBody.withReaderFont(),
                                    color = reader.ink,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_ROLL_STEP = 1f
private const val HOVER_PREVIEW_DELAY_MS = 350L
private val HOVER_PREVIEW_SIZE = 280.dp
