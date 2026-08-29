package com.orbin.uinext

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every screen in this module wraps itself in [NextTheme], so a theme around a screen is a theme
 * inside a theme. That has to resolve the way anyone would expect, and it did not: the parameter
 * defaulted to light, so an outer choice was thrown away by the first screen inside it and the
 * shipped app was light whatever the system said.
 *
 * Inheritance is not a detail here — it is the whole mechanism by which a reader's settings reach
 * this module, since the shell states them once and the screens say nothing. So each setting is
 * checked through a nested theme rather than on the one that states it.
 *
 * The goldens cannot see any of this. They wrap a screen in a theme and the screen overrode it, so
 * a "dark" capture came out light and looked like a light capture that was meant to be one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeNestingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a screen's own theme inherits the choice around it`() {
        assertTrue(paletteUnder { NextTheme(darkTheme = true) { it() } }.dark)
    }

    @Test
    fun `an explicit choice still wins over the one around it`() {
        assertFalse(paletteUnder { NextTheme(darkTheme = true) { NextTheme(darkTheme = false) { it() } } }.dark)
    }

    @Test
    fun `with nothing around it, it follows the system`() {
        assertFalse(paletteUnder { it() }.dark)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `and follows the system into the dark`() {
        assertTrue(paletteUnder { it() }.dark)
    }

    /** AMOLED is a ground, not a palette: true black behind the same ink and the same accent. */
    @Test
    fun `amoled reaches a nested screen and blackens the ground`() {
        lateinit var amoled: NextPalette
        lateinit var plain: NextPalette
        composeRule.setContent {
            NextTheme(darkTheme = true, amoled = true) { NextTheme { amoled = next } }
            NextTheme(darkTheme = true) { NextTheme { plain = next } }
        }
        composeRule.waitForIdle()

        assertTrue(amoled.amoled)
        assertEquals(Color.Black, amoled.background)
        assertNotEquals(plain.background, amoled.background)
        assertEquals(plain.ink, amoled.ink)
        assertEquals(plain.accent, amoled.accent)
    }

    /** AMOLED has nothing to do in a light theme, and must not quietly force a dark one. */
    @Test
    fun `amoled does nothing to a light theme`() {
        val palette = paletteUnder { NextTheme(darkTheme = false, amoled = true) { it() } }

        assertFalse(palette.dark)
        assertFalse(palette.amoled)
    }

    /**
     * The sizes in this module are literal `sp`, so the app's font-size preference can only reach
     * them through the density's own scale — which is what is asserted here. Pixels are not: the
     * platform applies a non-linear curve to `sp` at some scales and not others, so 1.5x the
     * setting is deliberately not 1.5x the pixels, and pinning pixels would pin that curve.
     */
    @Test
    fun `the font scale reaches a nested screen`() {
        var plain = 0f
        var scaled = 0f
        var plainPixels = 0f
        var scaledPixels = 0f
        composeRule.setContent {
            NextTheme {
                plain = LocalDensity.current.fontScale
                plainPixels = spPixels()
            }
            NextTheme(fontScale = 1.5f) {
                NextTheme {
                    scaled = LocalDensity.current.fontScale
                    scaledPixels = spPixels()
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(plain * 1.5f, scaled, 0.001f)
        // And it is text that got bigger, not just a number that changed.
        assertTrue(scaledPixels > plainPixels)
    }

    /**
     * A nested theme inherits a density the outer one has already scaled. Re-applying the factor
     * there would compound it once per screen, and every screen in this module wraps itself in a
     * theme — so this is the arrangement that would actually happen, not a contrived one.
     */
    @Test
    fun `an inherited scale is applied once, not once per screen`() {
        var once = 0f
        var thrice = 0f
        composeRule.setContent {
            NextTheme(fontScale = 1.5f) { once = LocalDensity.current.fontScale }
            NextTheme(fontScale = 1.5f) { NextTheme { NextTheme { thrice = LocalDensity.current.fontScale } } }
        }
        composeRule.waitForIdle()

        assertEquals(once, thrice, 0.001f)
    }

    /**
     * The scale already in force is what the app's preference multiplies, not what it replaces. A
     * reader who has enlarged text system-wide must not have that undone by this app.
     */
    @Test
    @Config(fontScale = 2f)
    fun `the app's scale multiplies the one already in force rather than replacing it`() {
        var system = 0f
        var scaled = 0f
        composeRule.setContent {
            system = LocalDensity.current.fontScale
            NextTheme(fontScale = 1.5f) { NextTheme { scaled = LocalDensity.current.fontScale } }
        }
        composeRule.waitForIdle()

        assertEquals(2f, system, 0.001f)
        assertEquals(3f, scaled, 0.001f)
    }

    @Composable
    private fun spPixels(): Float = with(LocalDensity.current) { 16.sp.toPx() }

    /** The palette a bare `NextTheme { }` — which is what every screen does — resolves to. */
    private fun paletteUnder(wrapper: @Composable (@Composable () -> Unit) -> Unit): NextPalette {
        lateinit var palette: NextPalette
        composeRule.setContent { wrapper { NextTheme { palette = next } } }
        composeRule.waitForIdle()
        return palette
    }
}
