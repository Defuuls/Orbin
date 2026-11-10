package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
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
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TikTokFeedAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun narrowMaximumTextKeepsCompleteTabsAndActionLabelsWithSeparateControls() {
        composeRule.setContent {
            NextTheme(fontScale = 2f) {
                Box(Modifier.size(248.dp, 600.dp)) {
                    FeedScreen(
                        rows = listOf(ROW),
                        onOpenBoards = {},
                        onOpenSearch = {},
                        onOpenSaved = {},
                        onOpenWatched = {},
                        onToggleWatch = {},
                        onToggleSaved = {},
                        onShareRow = {},
                        mediaContent = { _, modifier, _ ->
                            FeedPlaybackControls(true, true, {}, {}, modifier)
                        },
                    )
                }
            }
        }
        listOf("Following", "For You").forEach(::assertCompleteLabel)
        composeRule.onNodeWithTag(TikTokFeedTags.FOLLOWING).performClick().assertIsSelected()
        composeRule.onNodeWithTag(TikTokFeedTags.FOR_YOU).performClick().assertIsSelected()
        val menu = composeRule.onNodeWithContentDescription("Feed options").getUnclippedBoundsInRoot()
        val search =
            composeRule
                .onNode(
                    hasContentDescription("Search") and hasAnyAncestor(hasTestTag(TikTokFeedTags.HEADER)),
                ).getUnclippedBoundsInRoot()
        val mute = composeRule.onNodeWithTag(FeedPlaybackTags.MUTE).getUnclippedBoundsInRoot()
        assertTrue("Mute must clear the menu", mute.left >= menu.right)
        assertTrue("Mute must clear Search", mute.right <= search.left)
        val following = composeRule.onNodeWithTag(TikTokFeedTags.FOLLOWING).getUnclippedBoundsInRoot()
        assertTrue(
            "Enlarged tabs must sit below the icon controls",
            following.top >= maxOf(menu.bottom, search.bottom, mute.bottom),
        )
        val header = composeRule.onNodeWithTag(TikTokFeedTags.HEADER).getUnclippedBoundsInRoot()
        val rail = composeRule.onNodeWithTag(TikTokFeedTags.ACTIONS).getUnclippedBoundsInRoot()
        val caption = composeRule.onNodeWithTag(TikTokFeedTags.CAPTION).getUnclippedBoundsInRoot()
        assertTrue("The action rail must clear the enlarged header", rail.top >= header.bottom)
        assertTrue("The caption must clear the enlarged header", caption.top >= header.bottom)
        listOf(
            "Watch" to "Watch ${ROW.subject}",
            "Save" to "Save ${ROW.subject}",
            "Share" to "Share ${ROW.subject}",
        ).forEach { (label, description) ->
            composeRule
                .onNodeWithContentDescription(description)
                .performScrollTo()
                .assertIsDisplayed()
                .assertWidthIsAtLeast(MIN_TOUCH_TARGET)
                .assertHeightIsAtLeast(MIN_TOUCH_TARGET)
            assertCompleteLabel(label)
        }
    }

    private fun assertCompleteLabel(label: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule
            .onNodeWithText(label, useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.size)
        val layout = layouts.single()
        assertFalse(
            "$label must not overflow: size=${layout.size}, lines=${layout.lineCount}, " +
                "paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, input=${layout.layoutInput}",
            layout.hasVisualOverflow,
        )
        repeat(layout.lineCount) { line -> assertFalse("$label must not be ellipsized", layout.isLineEllipsized(line)) }
        assertEquals(
            "Every character of $label must be laid out",
            label.length,
            layout.getLineEnd(layout.lineCount - 1, visibleEnd = true),
        )
    }
}

private val ROW =
    FeedRow(
        id = "fourchan/g/7",
        subject = "Accessibility fixture",
        board = "/g/",
        site = "4chan",
        activity = "1m",
        replies = 12,
        media = 1,
        mediaLabel = "MP4",
    )
