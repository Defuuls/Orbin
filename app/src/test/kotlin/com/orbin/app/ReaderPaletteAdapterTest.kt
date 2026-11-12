package com.orbin.app

import com.orbin.uinext.canonicalNextPalette
import com.orbin.uinext.tokens.readerColors
import org.junit.Assert.assertEquals
import org.junit.Test

/** Both app roots use brightness and AMOLED alone to select the reader's token palette. */
class ReaderPaletteAdapterTest {
    @Test
    fun brightnessModesMapEveryPresentationRoleToReaderTokens() {
        listOf(false, true).forEach { dark ->
            listOf(false, true).forEach { amoled ->
                val colors = readerColors(dark, amoled)
                val palette = canonicalNextPalette(dark, amoled)
                assertEquals(colors.background, palette.background)
                assertEquals(colors.background, palette.raised)
                assertEquals(colors.background, palette.elevated)
                assertEquals(colors.ink, palette.ink)
                assertEquals(colors.inkMuted, palette.muted)
                assertEquals(colors.inkMuted, palette.faint)
                assertEquals(colors.hairline, palette.hairline)
                assertEquals(colors.accent, palette.accent)
                assertEquals(colors.onAccent, palette.onAccent)
                assertEquals(colors.selected, palette.accentSoft)
                assertEquals(colors.selected, palette.accentContainer)
                assertEquals(colors.ink, palette.onAccentContainer)
                assertEquals(colors.error, palette.destructive)
                assertEquals(colors.background, palette.onDestructive)
                assertEquals(colors.appBar, palette.appBar)
                assertEquals(colors.onAppBar, palette.onAppBar)
                assertEquals(colors.surfaceVariant, palette.surfaceVariant)
                assertEquals(colors.quote, palette.quote)
                assertEquals(colors.author, palette.author)
                assertEquals(colors.quoteLink, palette.quoteLink)
                assertEquals(dark, palette.dark)
                assertEquals(dark && amoled, palette.amoled)
            }
        }
    }
}
