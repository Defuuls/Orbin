package com.orbin.uinext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.orbin.uinext.components.PillVariant
import com.orbin.uinext.components.pillColors
import com.orbin.uinext.tokens.ReaderAmoledColors
import com.orbin.uinext.tokens.ReaderDarkColors
import com.orbin.uinext.tokens.ReaderLightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** One accent per brightness, and every pill's text is readable on its own ground (WCAG AA, 4.5:1). */
class PillContrastTest {
    private val themes =
        mapOf(
            "light" to ReaderLightColors,
            "dark" to ReaderDarkColors,
            "amoled" to ReaderAmoledColors,
        )

    @Test
    fun oneOrangeAccentInLightAndItsLighterToneInDarkThemes() {
        assertEquals(Color(0xFFC2410C), ReaderLightColors.accent)
        assertEquals(Color(0xFFFFFFFF), ReaderLightColors.onAccent)
        for (colors in listOf(ReaderDarkColors, ReaderAmoledColors)) {
            assertEquals(Color(0xFFFB923C), colors.accent)
            assertEquals(Color(0xFF431407), colors.onAccent)
        }
    }

    @Test
    fun whiteOnTheDarkThemeTintWouldFailSoItsTextIsDark() {
        assertTrue(contrast(Color.White, Color(0xFFFB923C)) < MIN_TEXT_CONTRAST)
        assertTrue(contrast(Color(0xFF431407), Color(0xFFFB923C)) >= MIN_TEXT_CONTRAST)
        assertTrue(contrast(Color.White, Color(0xFFC2410C)) >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun filledPillAndUnreadLabelTextMeetsAaOnTheAccent() {
        for ((mode, colors) in themes) {
            val pill = pillColors(PillVariant.Filled, colors)
            val ratio = contrast(pill.content, pill.container)
            assertTrue("$mode filled pill text is $ratio:1", ratio >= MIN_TEXT_CONTRAST)
        }
    }

    @Test
    fun tintedPillTextMeetsAaOnEveryAppSurface() {
        for ((mode, colors) in themes) {
            for (ground in listOf(colors.background, colors.surfaceVariant)) {
                val pill = pillColors(PillVariant.Tinted, colors)
                val ratio = contrast(pill.content, pill.container.compositeOver(ground))
                assertTrue("$mode tinted pill text is $ratio:1 over $ground", ratio >= MIN_TEXT_CONTRAST)
            }
        }
    }

    @Test
    fun mediaPillTextMeetsAaEvenOverAWhiteImage() {
        val pill = pillColors(PillVariant.Media, ReaderLightColors)
        val ratio = contrast(pill.content, pill.container.compositeOver(Color.White))
        assertTrue("media pill text is $ratio:1 over white", ratio >= MIN_TEXT_CONTRAST)
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }

    private companion object {
        const val MIN_TEXT_CONTRAST = 4.5f
    }
}
