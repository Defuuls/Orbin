package com.orbin.uinext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextSpace
import org.junit.Test
import kotlin.math.pow

/**
 * Tests ensuring total user interface uniformity across tokens, layouts, palettes,
 * and design system conventions.
 */
class UniformityTest {
    @Test
    fun `corner radius tokens maintain expressive roundness and uniformity`() {
        assertThat(NextRadius.tight).isEqualTo(8.dp)
        assertThat(NextRadius.control).isEqualTo(12.dp)
        assertThat(NextRadius.tile).isEqualTo(20.dp)
        assertThat(NextRadius.card).isEqualTo(24.dp)
        assertThat(NextRadius.sheet).isEqualTo(28.dp)
        assertThat(NextRadius.continuous).isEqualTo(28.dp)
        assertThat(NextRadius.pill).isEqualTo(100.dp)
    }

    @Test
    fun `spacing tokens maintain consistent grid and gutter rhythm`() {
        assertThat(NextSpace.gutter).isEqualTo(16.dp)
        assertThat(NextSpace.gutterTight).isEqualTo(16.dp)
        assertThat(NextSpace.section).isEqualTo(24.dp)
        assertThat(NextSpace.groupGap).isEqualTo(16.dp)
        assertThat(NextSpace.rowY).isEqualTo(14.dp)
        assertThat(NextSpace.rowX).isEqualTo(16.dp)
        assertThat(NextSpace.chromeInset).isEqualTo(16.dp)
        assertThat(NextSpace.chromeBottom).isEqualTo(12.dp)
    }

    @Test
    fun `all board hues maintain WCAG AA contrast across light and dark grounds`() {
        BoardHues.forEachIndexed { index, hue ->
            val lightRatio = contrastRatio(hue.light, LightPalette.background)
            val darkRatio = contrastRatio(hue.dark, DarkPalette.background)
            val amoledRatio = contrastRatio(hue.dark, AmoledPalette.background)

            assertWithMessage("Board hue $index light failed AA: $lightRatio")
                .that(lightRatio)
                .isAtLeast(4.5f)
            assertWithMessage("Board hue $index dark failed AA: $darkRatio")
                .that(darkRatio)
                .isAtLeast(4.5f)
            assertWithMessage("Board hue $index amoled failed AA: $amoledRatio")
                .that(amoledRatio)
                .isAtLeast(4.5f)
        }
    }

    @Test
    fun `destination tabs maintain complete semantic definitions`() {
        assertThat(NextDestination.entries).containsExactly(
            NextDestination.FEED,
            NextDestination.BOARDS,
            NextDestination.MEDIA,
            NextDestination.SETTINGS,
        )
    }

    private fun contrastRatio(
        a: Color,
        b: Color,
    ): Float {
        val lumA = relativeLuminance(a)
        val lumB = relativeLuminance(b)
        val lighter = maxOf(lumA, lumB)
        val darker = minOf(lumA, lumB)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun relativeLuminance(c: Color): Float {
        fun channel(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * channel(c.red) + 0.7152f * channel(c.green) + 0.0722f * channel(c.blue)
    }
}
