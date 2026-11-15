package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.ReaderAmoledColors
import com.orbin.uinext.tokens.ReaderColors
import com.orbin.uinext.tokens.ReaderDarkColors
import com.orbin.uinext.tokens.ReaderLightColors
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Paint the real controls; reading palette values alone cannot catch a hardcoded control color. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ReaderControlPaletteTest(
    private val mode: String,
) {
    @get:Rule
    val compose = createComposeRule()

    private val colors: ReaderColors
        get() =
            when (mode) {
                "dark" -> ReaderDarkColors
                "amoled" -> ReaderAmoledColors
                else -> ReaderLightColors
            }

    @Test
    fun checkedSettingsTogglePaintsTheScopedSettingsAccent() {
        compose.setContent {
            NextTheme(darkTheme = mode != "light", amoled = mode == "amoled") {
                SettingsScreen(
                    groups = listOf("Privacy" to listOf(SettingItem("lock", "App lock", "On", checked = true))),
                    showRail = false,
                )
            }
        }
        compose.onNodeWithContentDescription("Search settings").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("lock")
        val painted = compose.onNodeWithText("App lock").assertIsOn().paintedColors()
        val settingsAccent = if (mode == "light") Color(0xFFC2410C) else Color(0xFFFB923C)
        val settingsCard =
            when (mode) {
                "dark" -> Color(0xFF141520)
                "amoled" -> Color(0xFF0E0E10)
                else -> Color.White
            }
        assertTrue("$mode switch track uses the solid settings accent", settingsAccent.toArgb() in painted)
        assertTrue("$mode settings rows use their grouped-card surface", settingsCard.toArgb() in painted)
        assertTrue("$mode switch keeps its white thumb readable", Color.White.toArgb() in painted)
        assertFalse("A settings control must not retain the former palette", Color(0xFFFE2C55).toArgb() in painted)
    }

    @Test
    fun uncheckedIosSwitchKeepsItsThumbReadableOnTheReaderGround() {
        compose.setContent {
            NextTheme(darkTheme = mode != "light", amoled = mode == "amoled", platform = NextPlatform.IOS) {
                Box(Modifier.size(64.dp).background(colors.background)) {
                    PlatformSwitch(checked = false, onCheckedChange = null)
                }
            }
        }
        val painted = compose.onRoot().paintedColors()
        val offTrack = colors.inkMuted.copy(alpha = 0.24f).compositeOver(colors.background)
        assertTrue("$mode unchecked Cupertino switch keeps its white thumb", Color.White.toArgb() in painted)
        val expectedTrack = offTrack.toArgb()
        assertTrue(
            "$mode unchecked track stays visibly distinct below its white thumb",
            painted.any { actual ->
                listOf(0, 8, 16).all { shift ->
                    kotlin.math.abs((actual shr shift and 255) - (expectedTrack shr shift and 255)) <= 1
                }
            },
        )
    }

    @Test
    fun selectedHomeWatchUsesTheCurrentReaderAccentInEveryTheme() {
        val row = FeedRow("Palette fixture", "/g/", "1m", 2, 0, hasPreview = false, watched = true)
        compose.setContent {
            NextTheme(darkTheme = mode != "light", amoled = mode == "amoled") {
                FeedScreen(rows = listOf(row), onToggleWatch = {})
            }
        }
        compose.onNodeWithTag(ReaderFeedTags.more(row.id)).assertDoesNotExist()
        compose.onNodeWithTag(ReaderFeedTags.row(row.id)).performTouchInput { longClick() }
        val painted =
            compose
                .onNodeWithText("Stop watching Palette fixture")
                .assertIsSelected()
                .paintedColors()
        assertTrue("Home's selected mark must use the current reader theme", colors.accent.toArgb() in painted)
        assertFalse("Home must not retain the former selected-action color", Color(0xFFFE2C55).toArgb() in painted)
    }

    @Test
    fun focusedSearchPaintsTheReaderCursorAndSurfaceVariant() {
        compose.setContent {
            NextTheme(darkTheme = mode != "light", amoled = mode == "amoled") {
                var query by remember { mutableStateOf("") }
                Box(Modifier.size(320.dp, 64.dp).background(colors.background)) {
                    SchematicSearch(query, { query = it }, "Search palette")
                }
            }
        }
        // Freeze within the visible half of the real cursor blink cycle.
        compose.mainClock.autoAdvance = false
        compose.onNode(hasSetTextAction()).performClick()
        compose.mainClock.advanceTimeBy(100)
        val painted = compose.onRoot().paintedColors()
        assertTrue("$mode focused cursor must use reader accent", colors.accent.toArgb() in painted)
        assertTrue("$mode search ground must use surfaceVariant", colors.surfaceVariant.toArgb() in painted)
        assertFalse("Search must not retain the former cursor color", Color(0xFFFE2C55).toArgb() in painted)
    }

    private fun SemanticsNodeInteraction.paintedColors(): Set<Int> {
        val pixels = captureToImage().toPixelMap()
        return buildSet {
            for (y in 0 until pixels.height) {
                for (x in 0 until pixels.width) add(pixels[x, y].toArgb())
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun modes(): List<Array<String>> = listOf(arrayOf("light"), arrayOf("dark"), arrayOf("amoled"))
    }
}
