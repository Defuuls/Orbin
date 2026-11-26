package com.orbin.uinext.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.media_inline_fullscreen
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

data class ThreadMediaVisibility(
    val id: String,
    val fraction: Float,
    val centerDistance: Float,
)

/** Stable election keeps one decoder on screen, including when several short posts are visible. */
fun selectThreadInlineMedia(
    candidates: Collection<ThreadMediaVisibility>,
    requestedId: String? = null,
): String? {
    candidates.firstOrNull { it.id == requestedId && it.fraction >= MIN_MANUAL_VISIBLE_FRACTION }?.let { return it.id }
    return candidates
        .filter { it.fraction >= MIN_AUTOPLAY_VISIBLE_FRACTION }
        .sortedWith(
            compareByDescending<ThreadMediaVisibility> { it.fraction }
                .thenBy { it.centerDistance }
                .thenBy { it.id },
        ).firstOrNull()
        ?.id
}

@Composable
fun ThreadInlineFullscreenButton(
    id: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier =
            modifier
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                .testTag("thread-inline-fullscreen:$id"),
    ) {
        Icon(Icons.Filled.Fullscreen, stringResource(Res.string.media_inline_fullscreen), tint = Color.White)
    }
}

class ThreadInlineMediaState internal constructor() {
    private val candidates = mutableStateMapOf<String, ThreadMediaVisibility>()
    private var requested by mutableStateOf<String?>(null)

    // Every visible tile reports its bounds on every scroll frame. Electing once per change here,
    // rather than in each tile's composition, keeps scrolling from recomposing every media tile.
    private val elected by derivedStateOf { selectThreadInlineMedia(candidates.values, requested) }
    val activeId: String? get() = elected

    fun requestPlay(id: String) {
        requested = id
    }

    internal fun isRequested(id: String): Boolean = requested == id

    internal fun update(candidate: ThreadMediaVisibility) {
        if (candidates[candidate.id] != candidate) candidates[candidate.id] = candidate
    }

    internal fun remove(id: String) {
        candidates.remove(id)
        if (requested == id) requested = null
    }
}

@Composable
fun rememberThreadInlineMediaState(threadIdentity: String): ThreadInlineMediaState =
    remember(threadIdentity) { ThreadInlineMediaState() }

/**
 * Bounds are clipped by the scrolling ancestor and window before a media owner is elected.
 *
 * [content] is told it is active only once this tile has held the election for
 * [THREAD_INLINE_MEDIA_SETTLE_MILLIS], so a fling past a column of videos does not build and tear
 * down a player for every tile that crosses the middle of the screen. A tile the reader tapped
 * to play is active at once, and a tile that loses the election is inactive in the same frame,
 * so its player is released as soon as it scrolls away.
 */
@Composable
fun ThreadInlineMediaFrame(
    state: ThreadInlineMediaState,
    id: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(active: Boolean) -> Unit,
) {
    val window = LocalWindowInfo.current.containerSize
    DisposableEffect(state, id, enabled) { onDispose { state.remove(id) } }
    // Read through derivedStateOf: the tile recomposes when it wins or loses the election, not
    // on each scroll frame that moves the candidates.
    val elected by remember(state, id, enabled) { derivedStateOf { enabled && state.activeId == id } }
    var settled by remember(state, id) { mutableStateOf(false) }
    LaunchedEffect(state, id, elected) {
        if (!elected) {
            settled = false
            return@LaunchedEffect
        }
        if (!state.isRequested(id)) delay(THREAD_INLINE_MEDIA_SETTLE_MILLIS)
        settled = true
    }
    Box(
        modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            val visibleWidth =
                (bounds.right.coerceAtMost(window.width.toFloat()) - bounds.left.coerceAtLeast(0f))
                    .coerceAtLeast(0f)
            val visibleHeight =
                (bounds.bottom.coerceAtMost(window.height.toFloat()) - bounds.top.coerceAtLeast(0f))
                    .coerceAtLeast(0f)
            val area = coordinates.size.width.toFloat() * coordinates.size.height
            val fraction = if (enabled && area > 0f) (visibleWidth * visibleHeight / area).coerceIn(0f, 1f) else 0f
            state.update(ThreadMediaVisibility(id, fraction, abs(bounds.center.y - window.height / 2f)))
        },
    ) { content(elected && settled) }
}

/** How long a tile must stay elected before its player is built; shorter than a glance at it. */
const val THREAD_INLINE_MEDIA_SETTLE_MILLIS = 150L

private const val MIN_MANUAL_VISIBLE_FRACTION = 0.25f
private const val MIN_AUTOPLAY_VISIBLE_FRACTION = 0.6f
