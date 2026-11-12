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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The fixed app bar keeps drawer navigation reachable while Home rows scroll. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class RailHidesOnScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Replaces `TikTok navigation remains reachable while paging even with legacy hide preference`. */
    @Test
    fun `the drawer opens Saved after scrolling with the legacy hide preference enabled`() {
        assertSavedDrawerReachableAfterPaging(hidePreference = true)
    }

    /** Replaces `TikTok navigation remains reachable when the legacy hide preference is off`. */
    @Test
    fun `the drawer opens Saved after scrolling with the legacy hide preference disabled`() {
        assertSavedDrawerReachableAfterPaging(hidePreference = false)
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

    /** The catalog has no chrome that hides: its app bar actions stay reachable mid-scroll. */
    @Test
    fun `a catalog keeps its app bar actions after scrolling`() {
        var reloads = 0
        composeRule.setContent {
            NextTheme {
                BoardScreen(
                    board = "/g/",
                    description = "Technology",
                    itemCount = ROWS.size,
                    rowAt = { ROWS.getOrNull(it) },
                    onReload = { reloads += 1 },
                )
            }
        }
        composeRule.onNode(verticalScrollAction()).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Reload").assertIsDisplayed().performClick()
        assertTrue("expected one reload, got $reloads", reloads == 1)
    }

    private fun assertSavedDrawerReachableAfterPaging(hidePreference: Boolean) {
        var savedOpens = 0
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, onOpenSaved = { savedOpens++ }, hideRailOnScroll = hidePreference) }
        }
        composeRule.onNodeWithContentDescription("Menu").assertIsDisplayed()
        scrollFeedDown()
        composeRule.onNodeWithContentDescription("Menu").assertIsDisplayed().performClick()
        composeRule
            .onNodeWithTag(
                ReaderDrawerTags.ENTRY + NextDestination.SAVED.name,
            ).assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        assertTrue("Saved must remain wired after Home scrolls", savedOpens == 1)
        composeRule.onNodeWithTag(ReaderFeedTags.row(ROWS[12].id)).assertIsDisplayed()
    }

    private fun scrollFeedDown() {
        val grid = composeRule.onNodeWithTag(ReaderFeedTags.GRID)
        grid.performScrollToIndex(12)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReaderFeedTags.row(ROWS[12].id)).assertIsDisplayed()
        val offset = grid.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("Expected the feed to move down, offset=$offset", offset > 0f)
    }

    private fun verticalScrollAction() =
        hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private companion object {
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
