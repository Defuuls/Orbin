package com.orbin.uinext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.orbin.uinext.tokens.ReaderAmoledColors
import com.orbin.uinext.tokens.ReaderColors
import com.orbin.uinext.tokens.ReaderDarkColors
import com.orbin.uinext.tokens.ReaderLightColors
import com.orbin.uinext.tokens.readerColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Orbin's orange accent, magenta errors and blue quote links read at AA on every surface in every theme. */
class ReaderTokenContrastTest {
    private val themes = mapOf("light" to ReaderLightColors, "dark" to ReaderDarkColors, "AMOLED" to ReaderAmoledColors)

    @Test
    fun theDefaultIsOrangeWithMagentaErrors() {
        assertEquals(Color(0xFFC2410C), ReaderLightColors.accent)
        assertEquals(Color(0xFFBE185D), ReaderLightColors.error)
        assertEquals(Color(0xFFFFFFFF), ReaderLightColors.onError)
        listOf(ReaderDarkColors, ReaderAmoledColors).forEach { colors ->
            assertEquals(Color(0xFFFB923C), colors.accent)
            assertEquals(Color(0xFF431407), colors.onAccent)
            assertEquals(Color(0xFFFDBA74), colors.author)
            assertEquals(Color(0xFF7DD3FC), colors.quoteLink)
            assertEquals(Color(0xFFF472B6), colors.error)
            assertEquals(Color(0xFF500724), colors.onError)
        }
        // The default accent theme draws exactly these tokens.
        assertEquals(ReaderDarkColors, readerColors(dark = true, amoled = false, NextAccentTheme.ORBIN))
        assertEquals(ReaderLightColors, readerColors(dark = false, amoled = false, NextAccentTheme.ORBIN))
    }

    @Test
    fun filledControlTextMeetsAa() {
        themes.forEach { (name, colors) ->
            assertAa("$name onAccent on accent", colors.onAccent, colors.accent)
            assertAa("$name onError on error", colors.onError, colors.error)
        }
    }

    @Test
    fun authorQuoteLinkAndErrorMeetAaOnBackgroundAndCards() {
        themes.forEach { (name, colors) ->
            textTokens(colors).forEach { (token, ink) ->
                assertAa("$name $token on background", ink, colors.background)
                assertAa("$name $token on surfaceVariant", ink, colors.surfaceVariant)
            }
        }
    }

    @Test
    fun quoteLinksAndErrorsStayDistinctFromTheAccentAuthorsAndText() {
        themes.forEach { (name, colors) ->
            assertNotEquals("$name quoteLink vs author", colors.author, colors.quoteLink)
            assertNotEquals("$name quoteLink vs body", colors.ink, colors.quoteLink)
            assertNotEquals("$name error vs accent", colors.accent, colors.error)
        }
    }

    private fun textTokens(colors: ReaderColors) =
        listOf("author" to colors.author, "quoteLink" to colors.quoteLink, "error" to colors.error)

    private fun assertAa(
        what: String,
        foreground: Color,
        background: Color,
    ) {
        val first = foreground.luminance()
        val second = background.luminance()
        val ratio = (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
        assertTrue("$what is $ratio:1", ratio >= AA)
    }

    private companion object {
        const val AA = 4.5
    }
}
