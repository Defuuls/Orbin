package com.orbin.uinext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertWithMessage
import com.orbin.uinext.components.GridScrim
import org.junit.Test

/**
 * Rule: a grid card's white title must read at WCAG AA (4.5:1) even over a pure-white
 * thumbnail, anywhere the title can sit (from [GridScrim.TITLE_REGION_START] down).
 */
class GridScrimContrastTest {
    @Test
    fun `white title over a white thumbnail meets 4_5 to 1 across the whole title region`() {
        val steps = 100
        for (step in 0..steps) {
            val y = step / steps.toFloat()
            if (y < GridScrim.TITLE_REGION_START) continue
            val ground = Color.Black.copy(alpha = GridScrim.alphaAt(y)).compositeOver(Color.White)
            val ratio = contrast(Color.White, ground)
            assertWithMessage("contrast at ${"%.2f".format(y)} of card height").that(ratio).isAtLeast(4.5f)
        }
    }

    @Test
    fun `the scrim never lightens toward the bottom of the title region`() {
        val region = GridScrim.stops.filter { it.first >= GridScrim.TITLE_REGION_START }.map { it.second }
        assertWithMessage("alpha from title start down").that(region).isInOrder()
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Float = (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
}
