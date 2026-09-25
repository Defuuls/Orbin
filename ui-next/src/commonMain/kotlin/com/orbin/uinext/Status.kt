package com.orbin.uinext

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_status_loading
import com.orbin.uinext.resources.next_status_retry
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

/** Centered quiet spinner for in-pane loading (Search / Onboarding boards) — Next arc, not M3. */
@Composable
fun NextLoading(modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.next_status_loading)
    Box(
        modifier = modifier.fillMaxSize().semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        NextCircularProgress()
    }
}

/** Centered empty copy without Material icons or buttons. */
@Composable
fun NextEmpty(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(NextSpace.gutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = NextType.body,
            color = next.muted,
            textAlign = TextAlign.Center,
        )
    }
}

/** Centered error copy with optional Retry InlineAction. */
@Composable
fun NextError(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(NextSpace.gutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = NextType.body,
            color = next.muted,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            InlineAction(
                label = stringResource(Res.string.next_status_retry),
                accent = true,
                onClick = onRetry,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/**
 * Thin continuous progress track — Onboarding / Downloads / Gallery.
 *
 * Replaces Material LinearProgressIndicator so determinate bars read as Apple-thin hairlines
 * rather than M3 tracks. Pass [progress] in `0f..1f`, or omit for a soft indeterminate sweep.
 */
@Composable
fun NextLinearProgress(
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    val shape = RoundedCornerShape(NextRadius.pill)
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PROGRESS_HEIGHT)
                .clip(shape)
                .background(next.hairline),
    ) {
        if (progress != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(next.accent),
            )
        } else {
            val transition = rememberInfiniteTransition(label = "nextLinearIndeterminate")
            val shift by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 1100, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "nextLinearIndeterminateShift",
            )
            val barWidth = maxWidth * 0.35f
            val travel = maxWidth + barWidth
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(barWidth)
                        .offset(x = -barWidth + travel * shift)
                        .background(next.accent),
            )
        }
    }
}

private val PROGRESS_HEIGHT = 3.dp

/**
 * Settings-style On/Off control. Prefer this over Material Switch so preference rows match the
 * grouped Settings language (word toggles, not thumb tracks).
 */
@Composable
fun NextToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformSwitch(checked, onCheckedChange, modifier)
}
