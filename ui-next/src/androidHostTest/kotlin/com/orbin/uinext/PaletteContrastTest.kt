package com.orbin.uinext

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import kotlin.math.pow

/** All fourteen pairs from the design's Contrast table, measured through the public palette. */
class PaletteContrastTest {
    @Test
    fun `every reader contrast pair meets the spec minimum in all three modes`() {
        palettes().forEach { (name, palette) ->
            listOf(
                ContrastPair("onAppBar / appBar", palette.onAppBar, palette.appBar),
                ContrastPair("ink / background", palette.ink, palette.background),
                ContrastPair("inkMuted / background", palette.muted, palette.background),
                ContrastPair("ink / surfaceVariant", palette.ink, palette.surfaceVariant),
                ContrastPair("inkMuted / surfaceVariant", palette.muted, palette.surfaceVariant),
                ContrastPair("ink / selected", palette.ink, palette.accentContainer),
                ContrastPair("inkMuted / selected", palette.muted, palette.accentContainer),
                ContrastPair("quote / background", palette.quote, palette.background),
                ContrastPair("quote / selected", palette.quote, palette.accentContainer),
                ContrastPair("author / background", palette.author, palette.background),
                ContrastPair("quoteLink / background", palette.quoteLink, palette.background),
                ContrastPair("error / background", palette.destructive, palette.background),
                ContrastPair("accent / background", palette.accent, palette.background, minimum = 3.0),
                ContrastPair("onAccent / accent", palette.onAccent, palette.accent),
            ).forEach { pair -> assertRatio("$name ${pair.name}", pair.foreground, pair.background, pair.minimum) }
        }
    }

    @Test
    fun `compatibility text roles and Material controls keep accessible token pairings`() {
        palettes().forEach { (name, palette) ->
            listOf(palette.background, palette.raised, palette.elevated).forEach { ground ->
                listOf(palette.ink, palette.muted, palette.faint, palette.destructive).forEach { text ->
                    assertRatio("$name text", text, ground)
                }
            }
            assertThat(palette.faint).isEqualTo(palette.muted)
            assertRatio("$name selected", palette.onAccentContainer, palette.accentContainer)
            val material = materialColorScheme(palette, palette.dark, palette.amoled)
            assertRatio("$name error", material.error, material.surface)
            assertRatio("$name onError", material.onError, material.error)
            assertRatio("$name error container", material.onErrorContainer, material.errorContainer)
            assertRatio("$name primary", material.onPrimary, material.primary)
            assertRatio("$name selected Material control", material.onPrimaryContainer, material.primaryContainer)
        }
    }

    @Test
    fun `every board hue clears AA on the ground it is drawn on`() {
        BoardHues.forEachIndexed { index, hue ->
            assertRatio("board hue $index (light)", hue.light, LightPalette.background)
            assertRatio("board hue $index (dark)", hue.dark, DarkPalette.background)
            assertRatio("board hue $index (amoled)", hue.dark, AmoledPalette.background)
        }
    }

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

    private fun palettes(): List<Pair<String, NextPalette>> =
        listOf("light" to LightPalette, "dark" to DarkPalette, "amoled" to AmoledPalette)

    private fun assertRatio(
        what: String,
        foreground: Color,
        background: Color,
        minimum: Double = 4.5,
    ) {
        val measured = ratio(foreground, background)
        assertWithMessage("%s measured %s:1, needs %s:1", what, "%.2f".format(measured), minimum)
            .that(measured)
            .isAtLeast(minimum)
    }

    /** WCAG contrast includes compositing, so an accidental transparent token cannot evade it. */
    private fun ratio(
        foreground: Color,
        background: Color,
    ): Double {
        val composited =
            Color(
                red = foreground.red * foreground.alpha + background.red * (1f - foreground.alpha),
                green = foreground.green * foreground.alpha + background.green * (1f - foreground.alpha),
                blue = foreground.blue * foreground.alpha + background.blue * (1f - foreground.alpha),
            )
        val a = relativeLuminance(composited)
        val b = relativeLuminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private data class ContrastPair(
        val name: String,
        val foreground: Color,
        val background: Color,
        val minimum: Double = 4.5,
    )
}
