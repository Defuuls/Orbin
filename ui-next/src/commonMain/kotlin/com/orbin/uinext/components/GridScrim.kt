package com.orbin.uinext.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The darkening laid over a grid card's thumbnail so its white title and counts stay legible on
 * any image, including a pure-white one. Stops are (fraction of card height, black alpha).
 */
internal object GridScrim {
    /** Highest point a grid title can start; the scrim is at full title strength from here down. */
    const val TITLE_REGION_START = 0.2f
    private const val TOP_ALPHA = 0.12f
    private const val TITLE_ALPHA = 0.58f
    private const val BOTTOM_ALPHA = 0.85f

    val stops: List<Pair<Float, Float>> =
        listOf(
            0f to TOP_ALPHA,
            TITLE_REGION_START to TITLE_ALPHA,
            1f to BOTTOM_ALPHA,
        )

    val brush: Brush =
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = TOP_ALPHA),
            TITLE_REGION_START to Color.Black.copy(alpha = TITLE_ALPHA),
            1f to Color.Black.copy(alpha = BOTTOM_ALPHA),
        )

    fun alphaAt(y: Float): Float {
        val upper = stops.indexOfFirst { it.first >= y }.coerceAtLeast(1)
        val (y0, a0) = stops[upper - 1]
        val (y1, a1) = stops[upper]
        return if (y1 == y0) a1 else a0 + (a1 - a0) * ((y - y0) / (y1 - y0)).coerceIn(0f, 1f)
    }
}
