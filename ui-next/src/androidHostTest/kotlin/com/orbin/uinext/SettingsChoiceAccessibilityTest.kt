package com.orbin.uinext

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
@Config(sdk = [35], qualifiers = "w480dp-h800dp-mdpi")
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
                                        value = listOf("List", "Grid", "Card")[selected],
                                        kind = SettingKind.CHOICE,
                                        options = listOf("List", "Grid", "Card"),
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
    fun selectedFloatingChoicePaintsTheSettingsPaletteAndKeepsSelectionSemantics() {
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
                                        options = listOf("List", "Grid", "Card"),
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
            "Selected choices keep the readable settings text color",
            Color(0xFF14161F).toArgb() in colors,
        )
        assertTrue(
            "Selected choices show the indigo highlight on their white settings card",
            Color(0xFF4338CA).copy(alpha = 0.12f).compositeOver(Color.White).toArgb() in colors,
        )
        assertFalse("The removed palette must not color the choice", Color(0xFFFE2C55).toArgb() in colors)
    }

    private fun popupOption(label: String) =
        composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag("reader-settings-choice-popup")))

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun twoOptionSegmentsKeepOneRadioActionPerOptionAtLargeTextAndInRtl() {
        var scale by mutableFloatStateOf(1f)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        var width by mutableStateOf(411.dp)
        var selected by mutableIntStateOf(0)
        var activations = 0
        var selections = 0
        val options = listOf("Use app defaults", "Use custom playback")
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                NextTheme(fontScale = scale) {
                    SettingsScreen(
                        modifier = Modifier.width(width),
                        groups =
                            listOf(
                                "Playback" to
                                    listOf(
                                        SettingItem(
                                            "autoplay",
                                            "Playback behavior",
                                            options[selected],
                                            SettingKind.CHOICE,
                                            options = options,
                                            selected = selected,
                                        ),
                                    ),
                            ),
                        onActivate = { activations++ },
                        onSelectOption = { item, index ->
                            assertEquals("autoplay", item.id)
                            selected = index
                            selections++
                        },
                    )
                }
            }
        }
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        for (screenWidth in listOf(320.dp, 411.dp)) {
            for (fontScale in listOf(1f, 2f)) {
                for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                    composeRule.runOnIdle {
                        width = screenWidth
                        scale = fontScale
                        direction = layoutDirection
                    }
                    assertReadableSegments(options, selected, "$screenWidth/$fontScale/$layoutDirection")
                }
            }
        }
        composeRule
            .onNode(hasText(options[1]) and radio)
            .performScrollTo()
            .performClick()
            .assertIsSelected()
        composeRule.onNode(hasText(options[0]) and radio).assertIsNotSelected()
        composeRule.onNodeWithTag("reader-settings-choice-popup").assertDoesNotExist()
        assertEquals("Segments commit directly through the existing selection callback", 1, selections)
        assertEquals("The parent row adds no second activation", 0, activations)
    }

    private fun assertReadableSegments(
        options: List<String>,
        selected: Int,
        presentation: String,
    ) {
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        composeRule
            .onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("reader-setting-row-autoplay"))
        composeRule.onAllNodes(radio).assertCountEquals(2)
        composeRule.onNodeWithTag("reader-setting-row-autoplay").assert(!hasClickAction())
        val labelBounds =
            composeRule
                .onNodeWithText("Playback behavior", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        assertTrue(
            "Inline segments must leave room for their preference label",
            (labelBounds.right - labelBounds.left) > 0.dp,
        )
        options.forEachIndexed { index, label ->
            val option = composeRule.onNode(hasText(label) and radio)
            option.assertHeightIsAtLeast(48.dp)
            if (index == selected) option.assertIsSelected() else option.assertIsNotSelected()
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule
                .onNode(hasText(label) and hasAnyAncestor(radio), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse(
                "Option labels must wrap at $presentation",
                layouts.single().hasVisualOverflow,
            )
        }
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
        composeRule.onNodeWithTag(ReaderAppBarTags.TITLE).assertTextEquals("Settings")
    }
}
