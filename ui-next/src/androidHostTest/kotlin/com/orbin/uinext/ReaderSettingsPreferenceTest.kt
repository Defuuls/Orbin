package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
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
class ReaderSettingsPreferenceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun shortChoicesUseAnAnchoredMenuAndDismissWithoutCommitting() {
        var selections = 0
        compose.setContent {
            NextTheme {
                SettingsScreen(
                    groups = listOf("Appearance" to listOf(CATALOG)),
                    expandedId = "catalogMode",
                    onSelectOption = { _, _ -> selections++ },
                )
            }
        }
        compose
            .onNodeWithTag("reader-settings-choice-popup")
            .assertWidthIsEqualTo(250.dp)
        val menu = compose.onNodeWithTag("reader-settings-choice-popup").getUnclippedBoundsInRoot()
        listOf("List", "Grid", "Card").forEach { label ->
            val option =
                compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("reader-settings-choice-popup")))
            option.assertHeightIsAtLeast(48.dp).assertIsDisplayed()
            val bounds = option.getUnclippedBoundsInRoot()
            assertTrue(
                "$label remains fully visible vertically",
                bounds.top >= menu.top && bounds.bottom <= menu.bottom,
            )
            assertTrue(
                "$label remains fully visible horizontally",
                bounds.left >= menu.left && bounds.right <= menu.right,
            )
        }
        compose.onAllNodesWithText("Catalog view", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("reader-settings-choice-popup").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, selections) }
    }

    @Test
    @Config(qualifiers = "w248dp-h800dp-mdpi", fontScale = 2f)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun narrowMaximumTextKeepsTheWholeChoiceMenuVisibleAndSelectable() {
        var selectedIndex = -1
        compose.setContent {
            NextTheme {
                SettingsScreen(
                    groups = listOf("Appearance" to listOf(CATALOG)),
                    expandedId = "catalogMode",
                    onSelectOption = { _, index -> selectedIndex = index },
                )
            }
        }
        val menu = compose.onNodeWithTag("reader-settings-choice-popup").getUnclippedBoundsInRoot()
        assertTrue("The complete menu must fit a 248dp window", menu.right - menu.left <= 248.dp)
        listOf("List", "Grid", "Card").forEach { label ->
            compose
                .onNode(
                    hasText(label) and hasAnyAncestor(hasTestTag("reader-settings-choice-popup")),
                    useUnmergedTree = true,
                ).assertIsDisplayed()
            assertFalse("The complete choice label remains readable", choiceLayout(label).hasVisualOverflow)
        }
        compose.onNodeWithText("Grid").performClick()
        compose.onNodeWithTag("reader-settings-choice-popup").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, selectedIndex) }
    }

    @Test
    fun preferenceMenuAlignsToItsRowAndOpensAboveWhenTheLowerEdgeHasNoRoom() {
        val position = PreferencePopupPosition(8)
        assertEquals(
            IntOffset(24, 323),
            position.calculatePosition(
                IntRect(24, 241, 456, 315),
                IntSize(480, 800),
                LayoutDirection.Ltr,
                IntSize(250, 156),
            ),
        )
        assertEquals(
            IntOffset(206, 576),
            position.calculatePosition(
                IntRect(24, 740, 456, 814),
                IntSize(480, 800),
                LayoutDirection.Rtl,
                IntSize(250, 156),
            ),
        )
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aThemeScaleChangeEnlargesTheActualPopupChoiceExactlyOnce() {
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            NextTheme(fontScale = scale) {
                SettingsScreen(groups = listOf("Appearance" to listOf(CATALOG)), expandedId = "catalogMode")
            }
        }
        val normal = choiceLayout("Grid")
        // Android may reuse the popup's density object when the window recomposes. Read the
        // baseline values before changing scale rather than retaining that live density.
        val normalScale = normal.layoutInput.density.fontScale
        val normalFontSize = normal.layoutInput.style.fontSize
        val normalBaseline = normal.firstBaseline
        val normalLine = normal.getLineBottom(0) - normal.getLineTop(0)
        assertEquals(1f, normalScale, 0.001f)
        compose.runOnIdle { scale = 2f }
        val enlarged = choiceLayout("Grid")

        assertEquals(2f, enlarged.layoutInput.density.fontScale, 0.001f)
        assertEquals(normalFontSize, enlarged.layoutInput.style.fontSize)
        val enlargedLine = enlarged.getLineBottom(0) - enlarged.getLineTop(0)
        val metrics =
            "scale $normalScale -> ${enlarged.layoutInput.density.fontScale}; " +
                "baseline $normalBaseline -> ${enlarged.firstBaseline}; line $normalLine -> $enlargedLine"
        assertTrue("The popup glyph layout grows: $metrics", enlarged.firstBaseline > normalBaseline)
        assertTrue("The popup line height grows: $metrics", enlargedLine > normalLine)
    }

    @Test
    @Config(fontScale = 2f)
    fun anAlreadyEnlargedSystemScaleReachesThePopupWithoutMultiplication() {
        compose.setContent {
            NextTheme {
                NextTheme {
                    SettingsScreen(groups = listOf("Appearance" to listOf(CATALOG)), expandedId = "catalogMode")
                }
            }
        }
        assertEquals(2f, choiceLayout("Grid").layoutInput.density.fontScale, 0.001f)
    }

    @Test
    fun settingsSwitchKeepsTheReferenceVisualSize() {
        compose.setContent {
            NextTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.testTag("preference-switch")) { ReaderSettingsSwitch(true) }
                }
            }
        }
        compose.onNodeWithTag("preference-switch").assertWidthIsEqualTo(51.dp).assertHeightIsEqualTo(31.dp)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun settingsSwitchMirrorsItsWhiteThumbInRtl() {
        var direction by mutableStateOf(LayoutDirection.Ltr)
        var checked by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                NextTheme {
                    Box(Modifier.testTag("preference-switch")) { ReaderSettingsSwitch(checked) }
                }
            }
        }

        fun thumbCenter(): Float {
            val pixels = compose.onNodeWithTag("preference-switch").captureToImage().toPixelMap()
            val white = Color.White.toArgb()
            val positions =
                buildList {
                    for (y in 0 until pixels.height) {
                        for (x in 0 until pixels.width) if (pixels[x, y].toArgb() == white) add(x)
                    }
                }
            assertTrue("The settings thumb must remain solid white", positions.isNotEmpty())
            return positions.average().toFloat()
        }
        val checkedLtr = thumbCenter()
        compose.runOnIdle { direction = LayoutDirection.Rtl }
        val checkedRtl = thumbCenter()
        assertTrue("An enabled LTR switch places its thumb at the trailing edge", checkedLtr > 25f)
        assertTrue("RTL mirrors the enabled thumb", checkedRtl < 25f)
        assertEquals("Mirroring keeps the same thumb inset", 50f, checkedLtr + checkedRtl, 1f)
        compose.runOnIdle { checked = false }
        assertTrue("Disabling an RTL switch moves the thumb to its opposite edge", thumbCenter() > 25f)
    }

    @Test
    fun secretEditorsKeepPasswordSemanticsAndOnlyCommitThroughTheExistingCallback() {
        var committed = ""
        var expanded by mutableStateOf<String?>("token")
        compose.setContent {
            NextTheme {
                SettingsScreen(
                    groups =
                        listOf(
                            "Network" to
                                listOf(SettingItem("token", "Password", "Not set", SettingKind.TEXT, secret = true)),
                        ),
                    expandedId = expanded,
                    onCommitText = { item, value ->
                        assertEquals("token", item.id)
                        committed = value
                        expanded = null
                    },
                )
            }
        }
        compose
            .onNode(hasSetTextAction())
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
            .performTextInput("test-secret")
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertEquals("test-secret", committed) }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    private fun choiceLayout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose
            .onNode(
                hasText(text) and hasAnyAncestor(hasTestTag("reader-settings-choice-popup")),
                useUnmergedTree = true,
            ).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
}

private val CATALOG =
    SettingItem(
        "catalogMode",
        "Catalog view",
        "List",
        SettingKind.CHOICE,
        options = listOf("List", "Grid", "Card"),
        selected = 0,
    )
