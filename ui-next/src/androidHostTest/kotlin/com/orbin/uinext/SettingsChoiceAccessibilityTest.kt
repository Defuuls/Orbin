package com.orbin.uinext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
        popupOption("List").assert(radio).assertIsSelected()
        popupOption("Grid")
            .assert(radio)
            .assertIsNotSelected()
            .performClick()
        composeRule.onNodeWithText("Catalog view").performClick()
        popupOption("Grid").assertIsSelected().performClick()
        composeRule.onNodeWithText("Catalog view").performClick()
        popupOption("Grid").assertIsSelected()
        popupOption("List").assertIsNotSelected()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun selectedFloatingChoiceKeepsNeutralPreferenceTextAndSelectionSemantics() {
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
            popupOption("List")
                .assertIsSelected()
                .captureToImage()
                .toPixelMap()
        val colors =
            buildSet {
                for (y in 0 until pixels.height) {
                    for (x in 0 until pixels.width) add(pixels[x, y].toArgb())
                }
            }
        assertTrue(
            "The plain preference menu must use the neutral primary text",
            Color(0xDE000000).compositeOver(ReaderLightColors.background).toArgb() in colors,
        )
        assertFalse("The removed palette must not color the choice", TikTokDesign.Pink.toArgb() in colors)
    }

    private fun popupOption(label: String) =
        composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag("reader-settings-choice-popup")))

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
        composeRule.onNodeWithTag(ReaderAppBarTags.TITLE).assertTextEquals("Playback")
    }
}
