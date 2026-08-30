package com.orbin.uinext

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import kotlin.math.pow

/**
 * Holds the palette to WCAG AA's contrast floor.
 *
 * The ratios are recomputed here from the palette constants rather than recorded as expected
 * numbers, so the test fails when a colour is edited rather than when someone forgets to update a
 * fixture. It exists because the palette shipped below the floor and nothing noticed: [muted] and
 * [faint] measured 3.85:1 and 2.18:1 on the light ground, and 2.83:1 for dark's faint — the
 * timestamps, the reply counts and every read thread, in all three themes.
 *
 * Only colours that carry text are checked. [NextPalette.hairline] is deliberately excluded: it is
 * a separator between rows that are already separated by space, not information a reader has to
 * make out, and holding a hairline to 4.5:1 would draw a rule far heavier than the design wants.
 */
class PaletteContrastTest {
    @Test
    fun `every text colour clears AA on the light ground`() = assertPalette(LightPalette, "light")

    @Test
    fun `every text colour clears AA on the dark ground`() = assertPalette(DarkPalette, "dark")

    @Test
    fun `every text colour clears AA on the AMOLED ground`() = assertPalette(AmoledPalette, "amoled")

    @Test
    fun `every board hue clears AA on the ground it is drawn on`() {
        BoardHues.forEachIndexed { index, hue ->
            assertRatio("board hue $index (light)", hue.light, LightPalette.background)
            assertRatio("board hue $index (dark)", hue.dark, DarkPalette.background)
            assertRatio("board hue $index (amoled)", hue.dark, AmoledPalette.background)
        }
    }

    /**
     * The tiers stay distinguishable from each other, not just from the ground.
     *
     * Raising the alphas until each one passes would be satisfied by setting all three to full
     * ink, which would delete the hierarchy the palette is built on.
     */
    @Test
    fun `the three text tiers stay visibly separated`() {
        listOf(LightPalette, DarkPalette, AmoledPalette).forEach { palette ->
            val ink = ratio(palette.ink, palette.background)
            val muted = ratio(palette.muted, palette.background)
            val faint = ratio(palette.faint, palette.background)
            assertThat(ink).isGreaterThan(muted)
            assertThat(muted).isGreaterThan(faint)
        }
    }

    /** A pinned board keeps the hue it shipped with; anything else still lands in the table. */
    @Test
    fun `board hues are stable and in range`() {
        assertThat(boardHueIndex("/g/")).isEqualTo(0)
        assertThat(boardHueIndex("/ck/")).isEqualTo(1)
        listOf("/a/", "/biz/", "/vt/", "", "/x/", "/wsr/", "/уеб/").forEach { board ->
            val index = boardHueIndex(board)
            assertThat(index).isAtLeast(0)
            assertThat(index).isLessThan(BoardHues.size)
            assertThat(boardHueIndex(board)).isEqualTo(index)
        }
    }

    private fun assertPalette(
        palette: NextPalette,
        name: String,
    ) {
        assertRatio("$name ink", palette.ink, palette.background)
        assertRatio("$name muted", palette.muted, palette.background)
        assertRatio("$name faint", palette.faint, palette.background)
        assertRatio("$name accent", palette.accent, palette.background)
    }

    private fun assertRatio(
        what: String,
        foreground: Color,
        background: Color,
    ) {
        val measured = ratio(foreground, background)
        // Truth's message templates take %s only, so the ratio is formatted before it goes in.
        assertWithMessage("%s measured %s:1, needs %s:1", what, "%.2f".format(measured), AA_NORMAL_TEXT)
            .that(measured)
            .isAtLeast(AA_NORMAL_TEXT)
    }

    /**
     * WCAG 2.1 contrast, with the foreground first composited over the background.
     *
     * The compositing step is the part that matters here: the palette states its secondary tiers
     * as alphas over the ground, so measuring the declared colour rather than the drawn one would
     * report a ratio no reader ever sees.
     */
    private fun ratio(
        foreground: Color,
        background: Color,
    ): Double {
        val composited = composite(foreground, background)
        val a = relativeLuminance(composited)
        val b = relativeLuminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun composite(
        foreground: Color,
        background: Color,
    ): Color =
        Color(
            red = foreground.red * foreground.alpha + background.red * (1f - foreground.alpha),
            green = foreground.green * foreground.alpha + background.green * (1f - foreground.alpha),
            blue = foreground.blue * foreground.alpha + background.blue * (1f - foreground.alpha),
        )

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private companion object {
        /** WCAG 2.1 success criterion 1.4.3, for text below 18pt (or 14pt bold). */
        const val AA_NORMAL_TEXT = 4.5
    }
}
