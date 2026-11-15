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
    val activeId: String? get() = selectThreadInlineMedia(candidates.values, requested)

    fun requestPlay(id: String) {
        requested = id
    }

    internal fun update(candidate: ThreadMediaVisibility) {
        candidates[candidate.id] = candidate
    }

    internal fun remove(id: String) {
        candidates.remove(id)
        if (requested == id) requested = null
    }
}

@Composable
fun rememberThreadInlineMediaState(threadIdentity: String): ThreadInlineMediaState =
    remember(threadIdentity) { ThreadInlineMediaState() }

/** Bounds are clipped by the scrolling ancestor and window before a media owner is elected. */
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
    ) { content(enabled && state.activeId == id) }
}

private const val MIN_MANUAL_VISIBLE_FRACTION = 0.25f
private const val MIN_AUTOPLAY_VISIBLE_FRACTION = 0.6f
