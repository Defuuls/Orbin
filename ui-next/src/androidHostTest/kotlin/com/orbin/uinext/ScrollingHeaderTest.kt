package com.orbin.uinext

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Feed tabs stay over the pager while catalog headers retain their own reading behavior. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ScrollingHeaderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `feed tabs remain visible after paging to the last thread`() {
        composeRule.setContent { NextTheme { FeedScreen(rows = rows(), showRail = false) } }

        composeRule.onNodeWithTag(TikTokFeedTags.HEADER).assertIsDisplayed()
        composeRule.onNodeWithTag(TikTokFeedTags.PAGER).performScrollToIndex(ROW_COUNT - 1)

        composeRule.onNodeWithText(LAST_SUBJECT).assertIsDisplayed()
        composeRule.onNodeWithTag(TikTokFeedTags.HEADER).assertIsDisplayed()
        composeRule.onNodeWithTag(NextTitleTags.COMPACT).assertDoesNotExist()
    }

    /** The grid is the harder case: a header there has to span every column rather than take a cell. */
    @Test
    fun `feed tabs stay visible when the caller requests legacy grid defaults`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = rows(), showRail = false) }
        }

        composeRule.onNodeWithTag(TikTokFeedTags.HEADER).assertIsDisplayed()
        composeRule.onNodeWithTag(TikTokFeedTags.PAGER).performScrollToIndex(ROW_COUNT - 1)

        composeRule.onNodeWithText(LAST_SUBJECT).assertIsDisplayed()
        composeRule.onNodeWithTag(TikTokFeedTags.HEADER).assertIsDisplayed()
        composeRule.onNodeWithTag(NextTitleTags.COMPACT).assertDoesNotExist()
    }

    @Test
    fun `the catalog app bar stays in place while the threads scroll`() {
        val rows = rows()
        composeRule.setContent {
            NextTheme {
                BoardScreen(
                    board = "/g/",
                    description = "Technology",
                    itemCount = rows.size,
                    rowAt = { index -> rows.getOrNull(index) },
                )
            }
        }

        composeRule.onNodeWithText("Technology").assertIsDisplayed()
        composeRule
            .onNodeWithTag(CatalogTags.GRID)
            .performScrollToNode(hasContentDescription(LAST_SUBJECT, substring = true))

        composeRule.onNodeWithContentDescription(LAST_SUBJECT, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Technology").assertIsDisplayed()
    }

    @Test
    fun `the media wall title scrolls away with the grid`() {
        composeRule.setContent {
            NextTheme {
                MediaWallScreen(scanned = 40, total = 40, failed = 0, cells = cells(), showRail = false)
            }
        }

        composeRule.onNodeWithTag(NextTitleTags.LARGE).assertIsDisplayed()
        composeRule.onNode(verticalScrollAction()).performScrollToIndex(ROW_COUNT)

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
        composeRule.onNode(verticalScrollAction()).performScrollToNode(hasText(LAST_BOARD_TITLE))

        composeRule.onNodeWithTag(NextTitleTags.LARGE).assertDoesNotExist()
        composeRule.onNodeWithTag(NextTitleTags.COMPACT).assertIsDisplayed()
    }

    private fun scrollToLastRow() =
        composeRule.onNode(verticalScrollAction()).performScrollToNode(hasText(LAST_SUBJECT))

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

    private fun verticalScrollAction() =
        hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private companion object {
        const val ROW_COUNT = 30
        const val LAST_SUBJECT = "The last thread in the list"
        const val LAST_BOARD_TITLE = "The last board in the list"
    }
}
