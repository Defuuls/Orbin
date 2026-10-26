package com.orbin.uinext

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the shared theme consumed by both app roots, including live preference changes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrueBlackThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `all palettes honor mode and true black in a light system`() = assertModes(systemDark = false)

    @Test
    @Config(qualifiers = "+night")
    fun `all palettes honor mode and true black in a dark system`() = assertModes(systemDark = true)

    private fun assertModes(systemDark: Boolean) {
        val enabled = mutableStateOf(false)
        val observed = mutableMapOf<Triple<NextPlatform, NextColorTheme, String>, NextPalette>()
        composeRule.setContent {
            val system = isSystemInDarkTheme()
            NextPlatform.entries.forEach { platform ->
                NextColorTheme.entries.forEach { theme ->
                    mapOf("System" to system, "Light" to false, "Dark" to true).forEach { (mode, dark) ->
                        val palette = colorThemePalette(theme, dark, enabled.value)
                        NextTheme(palette = palette, platform = platform) {
                            NextTheme {
                                observed[Triple(platform, theme, mode)] = next
                                assertEquals(next.background, MaterialTheme.colorScheme.background)
                                assertEquals(next.background, MaterialTheme.colorScheme.surfaceContainerLowest)
                                assertEquals(next.raised, MaterialTheme.colorScheme.surface)
                                assertEquals(next.elevated, MaterialTheme.colorScheme.surfaceContainer)
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val plain = observed.toMap()
        listOf(true, false).forEach { amoled ->
            composeRule.runOnIdle { enabled.value = amoled }
            composeRule.waitForIdle()
            assertEquals(NextPlatform.entries.size * NextColorTheme.entries.size * 3, observed.size)
            observed.forEach { (key, palette) ->
                val dark = key.third == "Dark" || (key.third == "System" && systemDark)
                val original = plain.getValue(key)
                assertEquals("$key brightness", dark, palette.dark)
                assertEquals("$key AMOLED", dark && amoled, palette.amoled)
                assertEquals("$key accent", original.accent, palette.accent)
                assertEquals("$key selected accent", original.accentContainer, palette.accentContainer)
                if (dark && amoled) {
                    assertEquals("$key ground", Color.Black, palette.background)
                    assertNotEquals("$key retains raised surfaces", Color.Black, palette.raised)
                } else {
                    assertFalse(palette.amoled)
                    assertNotEquals("$key ground", Color.Black, palette.background)
                    assertEquals(original, palette)
                }
            }
        }
    }

    @Test
    fun `explicit palette cannot bypass a true black override`() {
        composeRule.setContent {
            NextColorTheme.entries.forEach { theme ->
                val palette = colorThemePalette(theme, dark = true)
                NextTheme(palette = palette, amoled = true) {
                    NextTheme {
                        assertEquals(Color.Black, next.background)
                        assertEquals(MaterialTheme.colorScheme.background, next.background)
                        assertEquals(palette.accent, next.accent)
                        assertEquals(true, next.amoled)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
