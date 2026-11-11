package com.orbin.uinext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.orbin.uinext.tokens.ReaderLightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsChoiceAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `catalog view choices expose selection and keep it after repeated selection`() {
        composeRule.setContent {
            var selected by remember { mutableIntStateOf(0) }
            NextTheme {
                SettingsScreen(
                    groups =
                        listOf(
                            "" to
                                listOf(
                                    SettingItem(
                                        id = "catalogMode",
                                        label = "Catalog view",
                                        value = listOf("List", "Grid")[selected],
                                        kind = SettingKind.CHOICE,
                                        options = listOf("List", "Grid"),
                                        selected = selected,
                                    ),
                                ),
                        ),
                    expandedId = "catalogMode",
                    showRail = false,
                    onSelectOption = { _, index -> selected = index },
                )
            }
        }

        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        composeRule.onNodeWithText("List").assert(radio).assertIsSelected()
        composeRule
            .onNodeWithText("Grid")
            .assert(radio)
            .assertIsNotSelected()
            .performClick()
        composeRule.onNodeWithText("Catalog view").performClick()
        composeRule.onNodeWithText("Grid").assertIsSelected().performClick()
        composeRule.onNodeWithText("Catalog view").performClick()
        composeRule.onNodeWithText("Grid").assertIsSelected()
        composeRule.onNodeWithText("List").assertIsNotSelected()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun selectedChoicePaintsTheReaderAccent() {
        composeRule.setContent {
            NextTheme(darkTheme = false) {
                SettingsScreen(
                    groups =
                        listOf(
                            "" to
                                listOf(
                                    SettingItem(
                                        id = "catalogMode",
                                        label = "Catalog view",
                                        value = "List",
                                        kind = SettingKind.CHOICE,
                                        options = listOf("List", "Grid"),
                                        selected = 0,
                                    ),
                                ),
                        ),
                    expandedId = "catalogMode",
                    showRail = false,
                )
            }
        }
        val pixels =
            composeRule
                .onNodeWithText("List")
                .assertIsSelected()
                .captureToImage()
                .toPixelMap()
        val colors =
            buildSet {
                for (y in 0 until pixels.height) {
                    for (x in 0 until pixels.width) add(pixels[x, y].toArgb())
                }
            }
        assertTrue("The selected choice must paint the reader accent", ReaderLightColors.accent.toArgb() in colors)
        assertFalse("The removed palette must not color the choice", TikTokDesign.Pink.toArgb() in colors)
    }

    @Test
    fun scopeSearchKeepsTheOriginalOptionIndex() {
        var selectedIndex = -1
        val options = listOf("App defaults") + (1..12).map { "Site · /board$it/" }
        composeRule.setContent {
            NextTheme {
                SettingsScreen(
                    groups =
                        listOf(
                            "Playback" to
                                listOf(
                                    SettingItem(
                                        id = "playbackScope",
                                        label = "Scope",
                                        value = options.first(),
                                        kind = SettingKind.CHOICE,
                                        options = options,
                                        selected = 0,
                                    ),
                                ),
                        ),
                    expandedId = "playbackScope",
                    onSelectOption = { _, index -> selectedIndex = index },
                )
            }
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("board12")
        composeRule.onNodeWithText("Site · /board12/").performClick()
        composeRule.waitForIdle()
        assertEquals(12, selectedIndex)
        composeRule.onNodeWithText("Playback").assertExists()
    }
}
