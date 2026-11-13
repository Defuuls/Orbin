package com.orbin.uinext

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

/** Reader source choice stays above the list while catalog headers retain their own reading behavior. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ScrollingHeaderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `source choice remains visible after scrolling to the last thread`() {
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    rows = rows(),
                    showRail = false,
                    sourceLabels = listOf("All followed sites", "4chan"),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Choose source").assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(ROW_COUNT - 1)

        composeRule.onNodeWithTag(ReaderFeedTags.row(rows().last().id)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Choose source").assertIsDisplayed()
        composeRule.onNodeWithTag(NextTitleTags.COMPACT).assertDoesNotExist()
    }

    /** The grid is the harder case: a header there has to span every column rather than take a cell. */
    @Test
    fun `source choice stays visible when the reader uses Grid mode`() {
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    rows = rows(),
                    showRail = false,
                    mode = CatalogMode.GRID,
                    sourceLabels = listOf("All followed sites", "4chan"),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Choose source").assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(ROW_COUNT - 1)

        composeRule.onNodeWithTag(ReaderFeedTags.row(rows().last().id)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Choose source").assertIsDisplayed()
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

        composeRule.onNodeWithText("/g/ - Technology").assertIsDisplayed()
        composeRule
            .onNodeWithTag(CatalogTags.GRID)
            .performScrollToNode(hasContentDescription(LAST_SUBJECT, substring = true))

        composeRule.onNodeWithContentDescription(LAST_SUBJECT, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("/g/ - Technology").assertIsDisplayed()
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
    fun `the boards Orbin wordmark stays in place while the list scrolls`() {
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

        composeRule
            .onNodeWithTag(BoardsTags.HEADER)
            .assertIsDisplayed()
            .assertContentDescriptionEquals(OrbinStyle.WORDMARK)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        // UIKit merges descendant labels too, so decorative text must not duplicate the header label.
        composeRule.onAllNodesWithText(OrbinStyle.WORDMARK, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNode(verticalScrollAction()).performScrollToNode(hasText(LAST_BOARD_TITLE))

        composeRule.onNodeWithText(LAST_BOARD_TITLE).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(OrbinStyle.WORDMARK).assertIsDisplayed()
        composeRule.onNodeWithTag(NextTitleTags.COMPACT).assertDoesNotExist()
    }

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
