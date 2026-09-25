package com.orbin.uinext

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A title is information, not furniture: it says what you opened and stops being useful once you
 * are reading. Every screen here is supposed to put its header *in* the content so it scrolls away
 * — and the feed, the board catalogue and the media wall did not, they pinned it in a band above
 * the list, which is the thing the whole interface argues against.
 *
 * The screenshot goldens cannot see this. They are all captured at rest, where a pinned header and
 * a first-item header are the same picture. So this scrolls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ScrollingHeaderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the feed header scrolls away with the list`() {
        composeRule.setContent { NextTheme { FeedScreen(rows = rows(), showRail = false) } }

        composeRule.onNodeWithTag(NextTitleTags.LARGE).assertIsDisplayed()
        scrollToLastRow()

        composeRule.onNodeWithTag(NextTitleTags.LARGE).assertDoesNotExist()
        composeRule.onNodeWithTag(NextTitleTags.COMPACT).assertDoesNotExist()
    }

    /** The grid is the harder case: a header there has to span every column rather than take a cell. */
    @Test
    fun `the feed header scrolls away in the grid`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = rows(), showRail = false) }
        }

        composeRule.onNodeWithTag(NextTitleTags.LARGE).assertIsDisplayed()
        scrollToLastRow()

        composeRule.onNodeWithTag(NextTitleTags.LARGE).assertDoesNotExist()
        composeRule.onNodeWithTag(NextTitleTags.COMPACT).assertDoesNotExist()
    }

    @Test
    fun `the board header scrolls away with the catalogue`() {
        val rows = rows()
        composeRule.setContent {
            NextTheme {
                BoardScreen(
                    board = "/g/",
                    description = "Technology",
                    itemCount = rows.size,
                    rowAt = { index -> rows.getOrNull(index) },
                    showRail = false,
                )
            }
        }

        composeRule.onNodeWithText("Technology").assertIsDisplayed()
        scrollToLastRow()

        composeRule.onNodeWithText("Technology").assertDoesNotExist()
    }

    @Test
    fun `the media wall title scrolls away with the grid`() {
        composeRule.setContent {
            NextTheme {
                MediaWallScreen(scanned = 40, total = 40, failed = 0, cells = cells(), showRail = false)
            }
        }

        composeRule.onNodeWithTag(NextTitleTags.LARGE).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToIndex(ROW_COUNT)

        composeRule.onNodeWithTag(NextTitleTags.LARGE).assertDoesNotExist()
        composeRule.onNodeWithTag(NextTitleTags.COMPACT).assertIsDisplayed()
    }

    @Test
    fun `the boards browse title scrolls away with the list`() {
        val boards =
            List(ROW_COUNT) { index ->
                BoardTile(
                    id = "b$index",
                    path = "/b$index/",
                    title = if (index == ROW_COUNT - 1) LAST_BOARD_TITLE else "Board $index",
                )
            }
        composeRule.setContent {
            NextTheme { BoardsScreen(boards = boards, showRail = false) }
        }

        composeRule.onNodeWithTag(NextTitleTags.LARGE).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(LAST_BOARD_TITLE))

        composeRule.onNodeWithTag(NextTitleTags.LARGE).assertDoesNotExist()
        composeRule.onNodeWithTag(NextTitleTags.COMPACT).assertIsDisplayed()
    }

    private fun scrollToLastRow() = composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(LAST_SUBJECT))

    private fun rows() =
        List(ROW_COUNT) { index ->
            FeedRow(
                subject = if (index == ROW_COUNT - 1) LAST_SUBJECT else "Thread number $index",
                board = "/g/",
                activity = "${index}m",
                replies = index,
                media = index,
                id = "row-$index",
            )
        }

    /** The last tile carries a board of its own, so scrolling to it is scrolling to the end. */
    private fun cells() =
        List(ROW_COUNT) { index ->
            MediaCell(board = if (index == ROW_COUNT - 1) "/z/" else "/g/", id = "cell-$index")
        }

    private companion object {
        const val ROW_COUNT = 30
        const val LAST_SUBJECT = "The last thread in the list"
        const val LAST_BOARD_TITLE = "The last board in the list"
    }
}
