package com.orbin.uinext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.orbin.uinext.components.NextAccentTheme
import com.orbin.uinext.components.canonicalNextPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentPaletteTest {
    @Test
    fun everyStableAccentHasReadableTextAndControlsInEveryAppearance() {
        assertEquals(15, NextAccentTheme.entries.size)
        NextAccentTheme.entries.forEach { accent ->
            listOf(false to false, true to false, true to true).forEach { (dark, oled) ->
                assertReadablePalette(accent, dark, oled)
            }
        }
    }

    private fun assertReadablePalette(
        accent: NextAccentTheme,
        dark: Boolean,
        oled: Boolean,
    ) {
        val palette = canonicalNextPalette(dark, oled, accent)
        listOf(palette.background, palette.raised, palette.accentContainer).forEach { ground ->
            listOf(palette.ink, palette.muted).forEach { text ->
                assertTrue("$accent/$dark/$oled text contrast", contrast(text, ground) >= 4.5f)
            }
        }
        assertTrue("$accent selected control", contrast(palette.onAccent, palette.accent) >= 4.5f)
        if (oled) assertEquals(Color.Black, palette.background)
        assertEquals(accent, palette.accentTheme)
        assertEquals(palette, canonicalNextPalette(dark, oled, accent.name))
    }

    @Test
    fun unknownFutureAccentKeepsTheExistingOrbinDefault() {
        assertEquals(canonicalNextPalette(false, false), canonicalNextPalette(false, false, "FUTURE_THEME"))
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Float = (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
}
