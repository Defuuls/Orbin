package com.orbin.uinext

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Stable
class ReaderInputState {
    private var editors by mutableStateOf(emptySet<Any>())
    val textEntryActive: Boolean get() = editors.isNotEmpty()

    internal fun focus(
        editor: Any,
        focused: Boolean,
    ) {
        editors = if (focused) editors + editor else editors - editor
    }
}

val LocalReaderInputState = staticCompositionLocalOf { ReaderInputState() }

/** Track real editor focus, including dialogs, so navigation never consumes an editing shortcut. */
fun Modifier.readerTextEntry(): Modifier =
    composed {
        val state = LocalReaderInputState.current
        val editor = remember { Any() }
        DisposableEffect(state, editor) { onDispose { state.focus(editor, false) } }
        onFocusChanged { state.focus(editor, it.isFocused) }
    }

@Composable
fun Modifier.readerKeyboardShortcuts(
    onBack: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onDestination: ((NextDestination) -> Unit)? = null,
): Modifier {
    val input = LocalReaderInputState.current
    val showSaved = LocalOrbinSavedMediaTabVisible.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!input.textEntryActive) focus.requestFocus() }
    return focusRequester(focus)
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || input.textEntryActive || event.isAltPressed) {
                false
            } else {
                val command = event.isMetaPressed || event.isCtrlPressed
                val action: (() -> Unit)? =
                    when {
                        event.key == Key.Escape -> onBack
                        command && event.key == Key.R -> onRefresh
                        command && event.key == Key.F -> onSearch
                        command && onDestination != null -> {
                            val index = listOf(Key.One, Key.Two, Key.Three, Key.Four).indexOf(event.key)
                            val destinations =
                                listOf(
                                    NextDestination.BOARDS,
                                    NextDestination.FEED,
                                    NextDestination.DOWNLOADS,
                                    NextDestination.SETTINGS,
                                ).filterNot { it == NextDestination.DOWNLOADS && !showSaved }
                            destinations.getOrNull(index)?.let { destination -> { onDestination(destination) } }
                        }
                        else -> null
                    }
                action?.invoke()
                action != null
            }
        }.focusable()
}

/** Secondary click opens the existing long-press action; it never activates the primary click. */
fun Modifier.readerSecondaryClick(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier =
    pointerInput(enabled, onClick) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                event.changes.forEach { it.consume() }
                onClick()
            }
        }
    }

/** Unclaimed horizontal drags go back; controls, pagers, image pans and vertical lists win first. */
fun Modifier.readerBackSwipe(onBack: (() -> Unit)?): Modifier =
    composed {
        if (onBack == null) return@composed this
        val back by rememberUpdatedState(onBack)
        val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
        val edge = with(LocalDensity.current) { 24.dp.toPx() }
        val minimum = with(LocalDensity.current) { 96.dp.toPx() }
        val input = LocalReaderInputState.current
        pointerInput(direction, edge, minimum, input) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!down.canStartReaderBackSwipe(input, size.width, edge)) {
                    return@awaitEachGesture
                }
                var x = 0f
                var y = 0f
                var claimed = false
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } > 1) break
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed && change.position != change.previousPosition) break
                    val delta = change.positionChange()
                    x += delta.x * direction
                    y += delta.y
                    if (!claimed && maxOf(abs(x), abs(y)) > viewConfiguration.touchSlop) {
                        if (x <= abs(y) * BACK_SWIPE_HORIZONTAL_BIAS) break
                        claimed = true
                    }
                    if (claimed) change.consume()
                    if (!change.pressed) {
                        if (claimed && x >= maxOf(minimum, size.width * BACK_SWIPE_WIDTH_FRACTION)) back()
                        break
                    }
                }
            }
        }
    }

private fun PointerInputChange.canStartReaderBackSwipe(
    input: ReaderInputState,
    width: Int,
    edge: Float,
): Boolean {
    if (type != PointerType.Touch || input.textEntryActive) return false
    return !(position.x < edge || position.x > width - edge)
}

private const val BACK_SWIPE_HORIZONTAL_BIAS = 1.25f
private const val BACK_SWIPE_WIDTH_FRACTION = 0.33f
