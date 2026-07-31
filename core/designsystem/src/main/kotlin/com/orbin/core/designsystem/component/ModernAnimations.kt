package com.orbin.core.designsystem.component

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modern skeleton loader with animated shimmer effect.
 * Used for content placeholder animation during loading.
 */
@Composable
fun SkeletonLoader(
    modifier: Modifier = Modifier,
    width: Dp = 200.dp,
    height: Dp = 16.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "skeleton_alpha",
    )

    Box(
        modifier =
            modifier
                .size(width = width, height = height)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                ).alpha(alpha),
    )
}

/**
 * Modern pulsing dot loader with 3 dots.
 * Animated loading indicator for async operations.
 */
@Composable
fun PulsingDotLoader(
    modifier: Modifier = Modifier,
    dotColor: Color = MaterialTheme.colorScheme.primary,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_dots")

    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(600, delayMillis = 0),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "dot1_alpha",
    )

    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(600, delayMillis = 200),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "dot2_alpha",
    )

    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(600, delayMillis = 400),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "dot3_alpha",
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
                    .alpha(dot1Alpha),
        )
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
                    .alpha(dot2Alpha),
        )
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
                    .alpha(dot3Alpha),
        )
    }
}

/**
 * Modern scaling circular progress indicator.
 * Animated loading state with scale effect.
 */
@Composable
fun ScalingProgressIndicator(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scaling_progress")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "scale",
    )

    Box(
        modifier =
            modifier
                .size(size)
                .scale(scale),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(size * 0.8f),
            strokeWidth = 3.dp,
        )
    }
}

/**
 * Modern loading state with animated skeleton loaders stacked vertically.
 * Used for list item loading placeholders.
 */
@Composable
fun LoadingSkeletonList(
    itemCount: Int = 3,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(itemCount) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonLoader(height = 16.dp)
                SkeletonLoader(width = 150.dp, height = 12.dp)
            }
        }
    }
}

/**
 * Modern floating action button loading state.
 * Shows circular progress indicator in a FAB-sized container.
 */
@Composable
fun FloatingActionButtonLoader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 2.dp,
        )
    }
}
