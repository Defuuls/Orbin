package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasSetTextAction
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
    fun shortChoicesUseAnAnchoredPlainMenuAndDismissWithoutCommitting() {
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
            .assertWidthIsEqualTo(448.dp)
            .assertHeightIsEqualTo(96.dp)
        compose.onAllNodesWithText("Catalog view", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("reader-settings-choice-popup").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, selections) }
    }

    @Test
    fun preferenceMenuPositionMatchesTheObservedOffsetAndKeepsTheWholeMenuOnScreen() {
        val position = PreferencePopupPosition(5)
        assertEquals(
            IntOffset(5, 246),
            position.calculatePosition(
                IntRect(0, 241, 480, 315),
                IntSize(480, 800),
                LayoutDirection.Ltr,
                IntSize(448, 192),
            ),
        )
        assertEquals(
            IntOffset(27, 608),
            position.calculatePosition(
                IntRect(0, 740, 480, 814),
                IntSize(480, 800),
                LayoutDirection.Rtl,
                IntSize(448, 192),
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
    fun compactSwitchKeepsTheActualPreferenceVisualSize() {
        compose.setContent {
            NextTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.testTag("preference-switch")) { ReaderSettingsSwitch(true) }
                }
            }
        }
        compose.onNodeWithTag("preference-switch").assertWidthIsEqualTo(46.dp).assertHeightIsEqualTo(27.dp)
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
            .onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
}

private val CATALOG =
    SettingItem(
        "catalogMode",
        "Catalog view",
        "List",
        SettingKind.CHOICE,
        options = listOf("List", "Grid"),
        selected = 0,
    )
