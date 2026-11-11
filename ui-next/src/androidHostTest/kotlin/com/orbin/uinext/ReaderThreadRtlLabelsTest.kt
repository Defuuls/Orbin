package com.orbin.uinext

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** RTL mirrors cell placement while protocol-like reference and count labels keep their glyph order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ReaderThreadRtlLabelsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `font two RTL backlinks and file badges draw canonical glyph order and remain actionable`() {
        var followed: String? = null
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                NextTheme {
                    ThreadScreen(
                        subject = "A thread",
                        board = "/g/",
                        posts =
                            listOf(
                                Post(
                                    number = "101",
                                    time = "2m",
                                    body = "Body",
                                    hasMedia = true,
                                    mediaCount = 3,
                                    replyNumbers = listOf("102"),
                                ),
                            ),
                        onPostReference = { followed = it },
                    )
                }
            }
        }
        val reference = layout(">>102")
        assertTrue(
            "The leading reference marker must draw before the digits",
            reference.getBoundingBox(0).left < reference.getBoundingBox(2).left,
        )
        val badge = layout("+2")
        assertTrue(
            "The plus must draw before the media count",
            badge.getBoundingBox(0).left < badge.getBoundingBox(1).left,
        )
        val thumbnail = composeRule.onNodeWithTag(ThreadTags.THUMBNAIL, true).getUnclippedBoundsInRoot()
        val badgeBounds = composeRule.onNodeWithText("+2", useUnmergedTree = true).getUnclippedBoundsInRoot()
        // BottomEnd remains at the thumbnail's left in RTL; only the label's internal direction changes.
        assertTrue(badgeBounds.left <= thumbnail.left + ReaderSpace.line * 2)
        assertTrue(badgeBounds.right <= thumbnail.right)
        composeRule
            .onNodeWithText(">>102")
            .assertHeightIsAtLeast(ReaderSize.touchTarget)
            .assertWidthIsAtLeast(ReaderSize.touchTarget)
            .performClick()
        assertEquals("102", followed)
    }

    private fun layout(label: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule
            .onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
}
