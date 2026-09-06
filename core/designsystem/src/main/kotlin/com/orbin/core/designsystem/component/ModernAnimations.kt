package com.orbin.core.designsystem.component

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Static skeleton placeholder used during loads.
 *
 * Infinite pulse/shimmer was dropped: on multi-column Pixel XL layouts the perpetual alpha
 * animation was visible as chrome noise more often than as useful progress feedback.
 */
@Composable
fun SkeletonLoader(
    modifier: Modifier = Modifier,
    width: Dp = 200.dp,
    height: Dp = 16.dp,
) {
    Box(
        modifier =
            modifier
                .size(width = width, height = height)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                ).alpha(SKELETON_ALPHA),
    )
}

/**
 * Compact three-dot idle marker (static). Prefer [CircularProgressIndicator] for determinate work.
 */
@Composable
fun PulsingDotLoader(
    modifier: Modifier = Modifier,
    dotColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(dotColor, CircleShape)
                        .alpha(DOT_ALPHAS[index]),
            )
        }
    }
}

/**
 * Plain circular progress — the previous infinite scale pulse was removed as load chrome noise.
 */
@Composable
fun ScalingProgressIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(size * 0.8f),
            strokeWidth = 3.dp,
        )
    }
}

/**
 * Stacked static skeleton placeholders for list loading states.
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
 * Floating action button loading state.
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

private const val SKELETON_ALPHA = 0.55f
private val DOT_ALPHAS = floatArrayOf(0.9f, 0.55f, 0.35f)
