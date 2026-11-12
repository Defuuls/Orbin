package com.orbin.uinext

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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

/** Both platforms share the same three token palettes, including live brightness changes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrueBlackThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `brightness modes and AMOLED follow a light system`() = assertModes(systemDark = false)

    @Test
    @Config(qualifiers = "+night")
    fun `brightness modes and AMOLED follow a dark system`() = assertModes(systemDark = true)

    private fun assertModes(systemDark: Boolean) {
        val enabled = mutableStateOf(false)
        val observed = mutableMapOf<Pair<NextPlatform, String>, NextPalette>()
        composeRule.setContent {
            val system = isSystemInDarkTheme()
            NextPlatform.entries.forEach { platform ->
                mapOf("System" to system, "Light" to false, "Dark" to true).forEach { (mode, dark) ->
                    ObserveMode(platform, mode, dark, enabled.value, observed)
                }
            }
        }
        composeRule.waitForIdle()
        listOf(true, false).forEach { amoled ->
            composeRule.runOnIdle { enabled.value = amoled }
            composeRule.waitForIdle()
            assertEquals(NextPlatform.entries.size * 3, observed.size)
            observed.forEach { (key, palette) ->
                val dark = key.second == "Dark" || (key.second == "System" && systemDark)
                assertPaletteMode(key, palette, dark, amoled)
            }
        }
    }

    @Composable
    private fun ObserveMode(
        platform: NextPlatform,
        mode: String,
        dark: Boolean,
        amoled: Boolean,
        observed: MutableMap<Pair<NextPlatform, String>, NextPalette>,
    ) {
        NextTheme(palette = canonicalNextPalette(dark, amoled), platform = platform) {
            NextTheme {
                observed[platform to mode] = next
                assertMaterialPalette(next, MaterialTheme.colorScheme)
            }
        }
    }

    private fun assertMaterialPalette(
        palette: NextPalette,
        material: ColorScheme,
    ) {
        assertEquals(palette.background, material.background)
        assertEquals(palette.background, material.surfaceContainerLowest)
        assertEquals(palette.raised, material.surface)
        assertEquals(palette.elevated, material.surfaceContainer)
        assertEquals(palette.surfaceVariant, material.surfaceVariant)
        if (palette.amoled) {
            listOf(
                material.background,
                material.surface,
                material.surfaceBright,
                material.surfaceDim,
                material.surfaceContainerLowest,
                material.surfaceContainerLow,
                material.surfaceContainer,
                material.surfaceContainerHigh,
                material.surfaceContainerHighest,
            ).forEach { ground -> assertEquals(Color.Black, ground) }
        }
    }

    private fun assertPaletteMode(
        key: Pair<NextPlatform, String>,
        palette: NextPalette,
        dark: Boolean,
        amoled: Boolean,
    ) {
        assertEquals("$key palette", canonicalNextPalette(dark, amoled), palette)
        if (dark && amoled) {
            listOf(palette.background, palette.raised, palette.elevated, palette.appBar).forEach { ground ->
                assertEquals("$key ground", Color.Black, ground)
            }
            assertNotEquals("$key placeholder fill", Color.Black, palette.surfaceVariant)
            assertNotEquals("$key selection fill", Color.Black, palette.accentContainer)
        } else {
            assertFalse(palette.amoled)
            assertNotEquals("$key ground", Color.Black, palette.background)
        }
    }

    @Test
    fun `an explicit palette cannot bypass an AMOLED override`() {
        composeRule.setContent {
            NextPlatform.entries.forEach { platform ->
                NextTheme(palette = canonicalNextPalette(true, false), amoled = true, platform = platform) {
                    NextTheme {
                        assertEquals(canonicalNextPalette(true, true), next)
                        assertEquals(Color.Black, next.background)
                        assertEquals(Color.Black, next.raised)
                        assertEquals(Color.Black, next.elevated)
                        assertEquals(MaterialTheme.colorScheme.background, next.background)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
