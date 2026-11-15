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
                material.surfaceDim,
                material.surfaceContainerLowest,
            ).forEach { ground -> assertEquals(Color.Black, ground) }
            listOf(
                material.surface,
                material.surfaceBright,
                material.surfaceContainerLow,
                material.surfaceContainer,
                material.surfaceContainerHigh,
                material.surfaceContainerHighest,
            ).forEach { ground -> assertEquals(Color(0xFF0E0E10), ground) }
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
            listOf(palette.background, palette.appBar).forEach { ground ->
                assertEquals("$key ground", Color.Black, ground)
            }
            listOf(palette.raised, palette.elevated, palette.surfaceVariant).forEach { ground ->
                assertEquals("$key lifted card ground", Color(0xFF0E0E10), ground)
            }
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
                        assertEquals(Color(0xFF0E0E10), next.raised)
                        assertEquals(Color(0xFF0E0E10), next.elevated)
                        assertEquals(Color.Black, next.appBar)
                        assertEquals(MaterialTheme.colorScheme.background, next.background)
                        assertMaterialPalette(next, MaterialTheme.colorScheme)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `an explicit brightness override resolves every role instead of retaining the supplied palette`() {
        composeRule.setContent {
            NextPlatform.entries.forEach { platform ->
                NextTheme(
                    palette = canonicalNextPalette(true, true),
                    darkTheme = false,
                    platform = platform,
                ) {
                    NextTheme {
                        assertEquals(canonicalNextPalette(false, false), next)
                        assertFalse(next.amoled)
                        assertNotEquals(Color.Black, next.background)
                        assertMaterialPalette(next, MaterialTheme.colorScheme)
                    }
                }
                NextTheme(
                    palette = canonicalNextPalette(false, false),
                    darkTheme = true,
                    amoled = true,
                    platform = platform,
                ) {
                    NextTheme {
                        assertEquals(canonicalNextPalette(true, true), next)
                        assertEquals(Color.Black, next.background)
                        assertMaterialPalette(next, MaterialTheme.colorScheme)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
