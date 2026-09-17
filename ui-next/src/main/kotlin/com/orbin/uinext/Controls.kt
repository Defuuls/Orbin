package com.orbin.uinext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.NextMotion
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Thin continuous slider — Feed / media density.
 *
 * Replaces Material [androidx.compose.material3.Slider] so size controls match
 * [NextLinearProgress] (hairline track, accent fill, soft circular thumb) instead of an M3 track.
 */
@Composable
fun NextSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    val range = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    var widthPx by remember { mutableFloatStateOf(0f) }
    val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)
    val trackColor = next.hairline
    val fillColor = next.accent
    val thumbFill = next.raised
    val hairline = next.hairline

    fun valueAt(x: Float): Float {
        if (widthPx <= 0f) return value
        val raw = (x / widthPx).coerceIn(0f, 1f)
        val continuous = valueRange.start + raw * range
        if (steps <= 0) return continuous
        val stepSize = range / (steps + 1)
        val stepped = ((continuous - valueRange.start) / stepSize).roundToInt() * stepSize
        return (valueRange.start + stepped).coerceIn(valueRange.start, valueRange.endInclusive)
    }

    Box(
        modifier =
            modifier
                .height(SLIDER_TOUCH_HEIGHT)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(valueRange, steps, widthPx) {
                    detectTapGestures { offset -> onValueChange(valueAt(offset.x)) }
                }.pointerInput(valueRange, steps, widthPx) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onValueChange(valueAt(change.position.x))
                    }
                },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(SLIDER_TRACK_HEIGHT)) {
            val trackY = size.height / 2f
            val radius = size.height / 2f
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, 0f),
                size = size,
                cornerRadius = CornerRadius(radius, radius),
            )
            val filled = size.width * fraction
            if (filled > 0f) {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(filled, size.height),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
            val thumbRadius = SLIDER_THUMB.toPx() / 2f
            val cx = (size.width * fraction).coerceIn(thumbRadius, size.width - thumbRadius)
            drawCircle(color = thumbFill, radius = thumbRadius, center = Offset(cx, trackY))
            drawCircle(
                color = hairline,
                radius = thumbRadius,
                center = Offset(cx, trackY),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/**
 * Thin arc spinner — matches [NextLinearProgress] stroke weight rather than a thick M3 indicator.
 */
@Composable
fun NextCircularProgress(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 28.dp,
) {
    val transition = rememberInfiniteTransition(label = "nextCircular")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "nextCircularSweep",
    )
    val accent = next.accent
    val track = next.hairline
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2f
        val arcSize = Size(this.size.width - stroke.width, this.size.height - stroke.width)
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = accent,
            startAngle = sweep - 90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
    }
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
        remember(isRefreshing, thresholdPx, onRefresh) {
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
                    val next = (pullPx + available.y * PULL_RESISTANCE).coerceAtMost(thresholdPx * 1.35f)
                    val consumedY = next - pullPx
                    pullPx = next
                    return Offset(0f, consumedY)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (isRefreshing) {
                        pullPx = 0f
                        return Velocity.Zero
                    }
                    val trigger = pullPx >= thresholdPx
                    pullPx = 0f
                    if (trigger) onRefresh()
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

/** Frosted bottom toast host — replaces Material [androidx.compose.material3.SnackbarHost]. */
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
                .background(next.raised.copy(alpha = if (next.dark) 0.92f else 0.94f))
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
                label = stringResource(R.string.next_snackbar_dismiss),
                onClick = data::dismiss,
            )
        }
    }
}

private val SLIDER_TRACK_HEIGHT = 3.dp
private val SLIDER_THUMB = 22.dp
private val SLIDER_TOUCH_HEIGHT = 40.dp
private val PULL_THRESHOLD = 64.dp
private const val PULL_RESISTANCE = 0.45f
private const val SNACKBAR_MS = 4_000L
