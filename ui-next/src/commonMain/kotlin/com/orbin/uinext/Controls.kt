package com.orbin.uinext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_snackbar_dismiss
import com.orbin.uinext.tokens.NextMotion
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.stringResource
import kotlin.coroutines.resume

/**
 * Modern Material 3 slider — Feed / media density.
 */
@Composable
fun NextSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    /** Accessibility label announced by TalkBack when focus lands on the slider. */
    contentDescription: String = "",
    /** True while a finger is dragging the thumb, so a caller can show what the drag points at. */
    onDragStateChange: (Boolean) -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) {
        onDragStateChange(isDragged)
    }

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        interactionSource = interactionSource,
        colors =
            SliderDefaults.colors(
                thumbColor = next.accent,
                activeTrackColor = next.accent,
                inactiveTrackColor = next.hairline,
            ),
        modifier =
            modifier.semantics {
                if (contentDescription.isNotEmpty()) {
                    this.contentDescription = contentDescription
                }
            },
    )
}

/**
 * Standard M3 circular loading indicator.
 */
@Composable
fun NextCircularProgress(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 28.dp,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = next.accent,
        trackColor = next.hairline,
        strokeWidth = 2.5.dp,
    )
}

/**
 * Pull-to-refresh with a thin [NextLinearProgress] indicator — Feed / Thread / All Media.
 *
 * Replaces Material3 [androidx.compose.material3.pulltorefresh.PullToRefreshBox] so refresh chrome
 * matches Next progress rather than an M3 spinner over the list.
 */
@Composable
fun NextPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val thresholdPx = with(density) { PULL_THRESHOLD.toPx() }
    var pullPx by remember { mutableFloatStateOf(0f) }
    val animatedPull = remember { Animatable(0f) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            animatedPull.animateTo(thresholdPx, tween(NextMotion.CHROME_MS, easing = NextMotion.Ease))
        } else if (pullPx == 0f) {
            animatedPull.animateTo(0f, tween(NextMotion.CHROME_MS, easing = NextMotion.Ease))
        }
    }

    LaunchedEffect(pullPx, isRefreshing) {
        if (!isRefreshing) {
            animatedPull.snapTo(pullPx)
        }
    }

    val connection =
        remember(isRefreshing, thresholdPx, onRefresh, haptics) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (isRefreshing || source != NestedScrollSource.UserInput) return Offset.Zero
                    // Collapse outstanding pull when the list scrolls up.
                    if (available.y >= 0f || pullPx <= 0f) return Offset.Zero
                    val consumed = available.y.coerceAtLeast(-pullPx)
                    pullPx = (pullPx + consumed).coerceAtLeast(0f)
                    return Offset(0f, consumed)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (isRefreshing || source != NestedScrollSource.UserInput) return Offset.Zero
                    if (available.y <= 0f) return Offset.Zero
                    val wasOver = pullPx >= thresholdPx
                    val next = (pullPx + available.y * PULL_RESISTANCE).coerceAtMost(thresholdPx * 1.35f)
                    val consumedY = next - pullPx
                    pullPx = next
                    if (!wasOver && next >= thresholdPx) {
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    }
                    return Offset(0f, consumedY)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (isRefreshing) {
                        pullPx = 0f
                        return Velocity.Zero
                    }
                    val trigger = pullPx >= thresholdPx
                    pullPx = 0f
                    if (trigger) {
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        onRefresh()
                    }
                    return Velocity.Zero
                }
            }
        }

    val showBar = isRefreshing || animatedPull.value > 1f
    val progress =
        when {
            isRefreshing -> null
            else -> (animatedPull.value / thresholdPx).coerceIn(0f, 1f)
        }

    Box(modifier = modifier.fillMaxSize().nestedScroll(connection)) {
        content()
        AnimatedVisibility(
            visible = showBar,
            enter = fadeIn(tween(NextMotion.CHROME_MS, easing = NextMotion.Ease)),
            exit = fadeOut(tween(NextMotion.CHROME_MS, easing = NextMotion.Ease)),
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = NextSpace.gutter, vertical = 8.dp),
        ) {
            NextLinearProgress(progress = progress)
        }
    }
}

/** Result of a [NextSnackbarHostState.showSnackbar] call. */
enum class NextSnackbarResult {
    Dismissed,
    ActionPerformed,
}

/**
 * Toast host state for Next chrome — Settings / Thread / root app.
 *
 * Replaces Material [androidx.compose.material3.SnackbarHostState] so call sites do not depend on
 * M3 snackbar types once [NextSnackbarHost] paints the toast.
 */
@Stable
class NextSnackbarHostState {
    private val mutex = Mutex()
    var current by mutableStateOf<NextSnackbarData?>(null)
        private set

    suspend fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        withDismissAction: Boolean = false,
    ): NextSnackbarResult =
        mutex.withLock {
            try {
                return suspendCancellableCoroutine { cont ->
                    current =
                        NextSnackbarData(
                            message = message,
                            actionLabel = actionLabel,
                            withDismissAction = withDismissAction,
                            continuation = cont,
                        )
                }
            } finally {
                current = null
            }
        }
}

@Stable
class NextSnackbarData internal constructor(
    val message: String,
    val actionLabel: String?,
    val withDismissAction: Boolean,
    private val continuation: CancellableContinuation<NextSnackbarResult>,
) {
    fun performAction() {
        if (continuation.isActive) continuation.resume(NextSnackbarResult.ActionPerformed)
    }

    fun dismiss() {
        if (continuation.isActive) continuation.resume(NextSnackbarResult.Dismissed)
    }
}

/**
 * Matte bottom toast host — replaces Material [androidx.compose.material3.SnackbarHost].
 *
 * Opaque [next.elevated] fill + hairline — not frosted glass. CMP has no portable UIBlurEffect
 * / RenderEffect blur path that is safe on both Android and iOS for this chrome, so the toast
 * stays solid matte (same vocabulary as [nextElevatedSurface]) rather than faking translucency.
 */
@Composable
fun NextSnackbarHost(
    hostState: NextSnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val data = hostState.current
    Box(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = data != null,
            enter =
                fadeIn(tween(NextMotion.CHROME_MS, easing = NextMotion.Ease)) +
                    slideInVertically(tween(NextMotion.CHROME_MS, easing = NextMotion.Ease)) { it / 2 },
            exit =
                slideOutVertically(tween(NextMotion.CHROME_MS, easing = NextMotion.Ease)) { it / 2 } +
                    fadeOut(tween(NextMotion.CHROME_MS, easing = NextMotion.Ease)),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = NextSpace.gutter, vertical = NextSpace.chromeBottom),
        ) {
            val snack = data
            if (snack != null) {
                NextSnackbarToast(data = snack)
                LaunchedEffect(snack) {
                    kotlinx.coroutines.delay(SNACKBAR_MS)
                    snack.dismiss()
                }
            }
        }
    }
}

@Composable
private fun NextSnackbarToast(data: NextSnackbarData) {
    val shape = RoundedCornerShape(NextRadius.continuous)
    Row(
        modifier =
            Modifier
                .widthIn(max = 480.dp)
                .clip(shape)
                .background(next.elevated)
                .border(0.5.dp, next.hairline, shape)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { contentDescription = data.message },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = data.message,
            style = NextType.footnote,
            color = next.ink,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (data.actionLabel != null) {
            InlineAction(
                label = data.actionLabel,
                accent = true,
                onClick = data::performAction,
            )
        }
        if (data.withDismissAction) {
            InlineAction(
                label = stringResource(Res.string.next_snackbar_dismiss),
                onClick = data::dismiss,
            )
        }
    }
}

private val PULL_THRESHOLD = 64.dp
private const val PULL_RESISTANCE = 0.45f
private const val SNACKBAR_MS = 4_000L
