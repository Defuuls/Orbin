package com.orbin.uinext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Measures text in the actual separate dialog window, rather than trusting a fixture name. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w390dp-h844dp-mdpi")
class OrbinDialogTextScaleTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aThemeScaleChangeEnlargesTheActualDialogTitleAndBodyExactlyOnce() {
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            NextTheme(fontScale = scale) {
                OrbinDialog(onDismissRequest = {}) {
                    OrbinDialogTitle("Title")
                    OrbinDialogMessage("Body")
                }
            }
        }
        val normal = listOf(layout("Title"), layout("Body"))
        compose.runOnIdle { scale = 2f }
        val enlarged = listOf(layout("Title"), layout("Body"))
        normal.zip(enlarged).forEach { (before, after) ->
            assertEquals(1f, before.layoutInput.density.fontScale, 0.001f)
            assertEquals(2f, after.layoutInput.density.fontScale, 0.001f)
            assertEquals(before.layoutInput.style.fontSize, after.layoutInput.style.fontSize)
            assertTrue("The glyph layout grows", after.firstBaseline > before.firstBaseline)
            val normalLine = before.getLineBottom(0) - before.getLineTop(0)
            val enlargedLine = after.getLineBottom(0) - after.getLineTop(0)
            assertTrue("The actual line height grows", enlargedLine > normalLine)
        }
    }

    @Test
    @Config(fontScale = 2f)
    fun anAlreadyEnlargedSystemScaleIsPreservedWithoutMultiplication() {
        compose.setContent {
            NextTheme {
                NextTheme {
                    OrbinDialog(onDismissRequest = {}) { OrbinDialogTitle("System title") }
                }
            }
        }
        assertEquals(2f, layout("System title").layoutInput.density.fontScale, 0.001f)
    }

    @Test
    fun anActionSheetKeepsTheThemeScaleAcrossItsSeparateWindow() {
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            NextTheme(fontScale = scale) {
                OrbinActionSheet("Actions", listOf(NextSheetAction("Save", {})), onDismiss = {})
            }
        }
        val normal = listOf(layout("Actions"), layout("Save"))
        compose.runOnIdle { scale = 2f }
        val enlarged = listOf(layout("Actions"), layout("Save"))
        normal.zip(enlarged).forEach { (before, after) ->
            assertEquals(1f, before.layoutInput.density.fontScale, 0.001f)
            assertEquals(2f, after.layoutInput.density.fontScale, 0.001f)
            assertEquals(before.layoutInput.style.fontSize, after.layoutInput.style.fontSize)
            assertTrue("Sheet glyphs grow with the outer theme", after.firstBaseline > before.firstBaseline)
            assertTrue("Sheet line height grows", after.getLineBottom(0) > before.getLineBottom(0))
        }
    }

    @Test
    fun theFeedSourceSheetKeepsTheActualEnlargedChoiceText() {
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            NextTheme(fontScale = scale) {
                FeedSourceSheet(listOf("4chan", "BBWChan"), selected = 0, onSelect = {}, onDismiss = {})
            }
        }
        val normal = layout("4chan")
        compose.runOnIdle { scale = 2f }
        val enlarged = layout("4chan")
        assertEquals(1f, normal.layoutInput.density.fontScale, 0.001f)
        assertEquals(2f, enlarged.layoutInput.density.fontScale, 0.001f)
        assertEquals(normal.layoutInput.style.fontSize, enlarged.layoutInput.style.fontSize)
        assertTrue(enlarged.firstBaseline > normal.firstBaseline)
        assertTrue(enlarged.getLineBottom(0) > normal.getLineBottom(0))
    }

    @Test
    @Config(qualifiers = "en-rUS-w248dp-h600dp-mdpi")
    fun aLongNarrowDialogAtMaximumTextScrollsToBothWorkingActions() {
        var confirmations = 0
        var dismissals = 0
        compose.setContent {
            NextTheme(fontScale = 2f) {
                OrbinConfirmDialog(
                    title = "Clear download history?",
                    message = "The downloaded files stay on this device. ".repeat(12),
                    confirmLabel = "Clear",
                    onConfirm = { confirmations++ },
                    onDismiss = { dismissals++ },
                )
            }
        }
        val dialog = compose.onNode(isDialog()).getUnclippedBoundsInRoot()
        listOf("Cancel", "Clear").forEach { label ->
            val action = compose.onNodeWithText(label)
            action
                .performScrollTo()
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
            val bounds = action.getUnclippedBoundsInRoot()
            assertTrue("$label stays inside the dialog", bounds.left >= dialog.left && bounds.right <= dialog.right)
            action.performClick()
        }
        assertEquals(1, dismissals)
        assertEquals(1, confirmations)
    }

    private fun layout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
}
