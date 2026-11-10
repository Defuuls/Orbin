package com.orbin.uinext

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fixed TikTok navigation survives paging; secondary catalogs retain their scroll reporting. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class RailHidesOnScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `TikTok navigation remains reachable while paging even with legacy hide preference`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, onOpenSaved = {}, hideRailOnScroll = true) }
        }
        composeRule.onNodeWithContentDescription(SAVED_TAB).assertExists()
        scrollFeedDown()
        composeRule.onNodeWithContentDescription(SAVED_TAB).assertExists()
    }

    @Test
    fun `TikTok navigation remains reachable when the legacy hide preference is off`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, onOpenSaved = {}, hideRailOnScroll = false) }
        }
        scrollFeedDown()
        composeRule.onNodeWithContentDescription(SAVED_TAB).assertExists()
    }

    @Test
    fun `the media wall names itself once, with no floating bar repeating the title`() {
        composeRule.setContent {
            NextTheme {
                MediaWallScreen(
                    scanned = 4,
                    total = 4,
                    failed = 0,
                    cells = (1..40).map { MediaCell(id = "$it", board = "/g/") },
                )
            }
        }
        composeRule.onAllNodesWithText("All media").assertCountEquals(1)
    }

    /** The screen reports the change, so the shell can take the system bars with it. */
    @Test
    fun `a screen tells the shell when its chrome goes away`() {
        val reported = mutableListOf<Boolean>()
        composeRule.setContent {
            NextTheme {
                BoardScreen(
                    board = "/g/",
                    description = "Technology",
                    itemCount = ROWS.size,
                    rowAt = { ROWS.getOrNull(it) },
                    hideRailOnScroll = true,
                    onChromeVisibleChange = { reported += it },
                )
            }
        }
        composeRule.onNode(verticalScrollAction()).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assert(reported.contains(false)) { "expected the screen to report hidden chrome, got $reported" }
    }

    private fun scrollFeedDown() {
        val grid = composeRule.onNodeWithTag(TikTokFeedTags.PAGER)
        grid.performScrollToIndex(12)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Thread number 13").assertIsDisplayed()
        val offset = grid.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("Expected the feed to move down, offset=$offset", offset > 0f)
    }

    private fun verticalScrollAction() =
        hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private companion object {
        const val SAVED_TAB = "Saved"

        val ROWS =
            (1..30).map { index ->
                FeedRow(
                    subject = "Thread number $index",
                    board = "/g/",
                    activity = "${index}m",
                    replies = index,
                    media = index,
                )
            }
    }
}
