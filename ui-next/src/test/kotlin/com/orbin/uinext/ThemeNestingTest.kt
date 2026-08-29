package com.orbin.uinext

import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertFalse
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
 * The goldens cannot see this — they wrap the screen in a theme and the screen overrode it, so a
 * "dark" capture came out light and looked like a light capture that was supposed to be one.
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

    /** The palette a bare `NextTheme { }` — which is what every screen does — resolves to. */
    private fun paletteUnder(
        wrapper: @androidx.compose.runtime.Composable (@androidx.compose.runtime.Composable () -> Unit) -> Unit,
    ): NextPalette {
        lateinit var palette: NextPalette
        composeRule.setContent { wrapper { NextTheme { palette = next } } }
        composeRule.waitForIdle()
        return palette
    }
}
