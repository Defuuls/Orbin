package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Counts reflow at large text sizes; only catalog titles may use an ellipsis. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CatalogLargeTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `normal catalog cards span the phone with an image half the viewport tall`() {
        composeRule.setContent { Grid(fontScale = 1f) }

        val grid = composeRule.onNodeWithTag(CatalogTags.GRID).getUnclippedBoundsInRoot()
        val cards = composeRule.onAllNodesWithTag(CatalogTags.CARD)
        val images = composeRule.onAllNodesWithTag(CatalogTags.THUMBNAIL, useUnmergedTree = true)
        LABELS.forEachIndexed { index, label ->
            val layout = countLayout(label)
            assertEquals(1, layout.lineCount)
            assertFalse(overflowDetails(label, layout), layout.hasVisualOverflow)
            val card = cards[index].getUnclippedBoundsInRoot()
            val image = images[index].getUnclippedBoundsInRoot()
            // 411dp less the 2dp outer margin at each edge: one column.
            assertEquals(407f, (card.right - card.left).value, 0.5f)
            assertEquals((grid.bottom - grid.top).value / 2f, (image.bottom - image.top).value, 0.5f)
            assertTrue(
                "the text block keeps its minimum below the image",
                (card.bottom - image.bottom) >= ReaderSize.gridCardText,
            )
        }
    }

    @Test
    fun `font two keeps complete counts below a full-width image of unchanged height`() {
        composeRule.setContent { Grid(fontScale = 2f, longTitles = true, longCounts = true) }

        val grid = composeRule.onNodeWithTag(CatalogTags.GRID).getUnclippedBoundsInRoot()
        val cards = composeRule.onAllNodesWithTag(CatalogTags.CARD)
        val images = composeRule.onAllNodesWithTag(CatalogTags.THUMBNAIL, useUnmergedTree = true)
        LONG_LABELS.forEachIndexed { index, label ->
            val layout = countLayout(label)
            assertEquals(label, layout.layoutInput.text.text)
            assertFalse(overflowDetails(label, layout), layout.hasVisualOverflow)
            assertEquals(label.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
            repeat(layout.lineCount) { assertFalse(layout.isLineEllipsized(it)) }

            val text = composeRule.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val card = cards[index].getUnclippedBoundsInRoot()
            val image = images[index].getUnclippedBoundsInRoot()
            assertEquals(
                "Large text never shrinks the image",
                (grid.bottom - grid.top).value / 2f,
                (image.bottom - image.top).value,
                0.5f,
            )
            assertTrue(
                "The text block must grow rather than clip the count",
                (card.bottom - image.bottom) > ReaderSize.gridCardText,
            )
            assertTrue(text.top >= image.bottom + ReaderSpace.cardPad)
            assertTrue(text.bottom <= card.bottom - ReaderSpace.cardPad)
        }
    }

    @Test
    fun `font two wraps complete reply and unread counts inside a short pane's three narrow cards`() {
        // 250dp leaves less than 240dp below the app bar, so images sit at the 120dp floor, three across.
        composeRule.setContent {
            Box(Modifier.size(360.dp, 250.dp)) { Grid(fontScale = 2f, longTitles = true, longCounts = true) }
        }

        val cards = composeRule.onAllNodesWithTag(CatalogTags.CARD)
        val images = composeRule.onAllNodesWithTag(CatalogTags.THUMBNAIL, useUnmergedTree = true)
        LONG_LABELS.forEachIndexed { index, label ->
            val layout = countLayout(label)
            assertEquals(label, layout.layoutInput.text.text)
            assertEquals(
                "Counters retain their specified sp size",
                if (index == 0) 14.sp else 12.sp,
                layout.layoutInput.style.fontSize,
            )
            assertEquals(2f, layout.layoutInput.density.fontScale, 0f)
            assertFalse(overflowDetails(label, layout), layout.hasVisualOverflow)
            assertTrue("$label should wrap in this three-column grid", layout.lineCount > 1)
            assertEquals(label.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
            repeat(layout.lineCount) { assertFalse(layout.isLineEllipsized(it)) }

            val text = composeRule.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val card = cards[index].getUnclippedBoundsInRoot()
            val image = images[index].getUnclippedBoundsInRoot()
            assertEquals("three 116dp cards share the line", 116f, (card.right - card.left).value, 0.5f)
            assertEquals(ReaderSize.gridCardMin.value, (image.bottom - image.top).value, 0.5f)
            assertTrue(
                "The text block must grow rather than clip the count",
                (card.bottom - image.bottom) > ReaderSize.gridCardText,
            )
            assertTrue(text.left >= card.left + ReaderSpace.cardPad)
            assertTrue(text.right <= card.right - ReaderSpace.cardPad)
            assertTrue(text.top >= image.bottom + ReaderSpace.cardPad)
            assertTrue(text.bottom <= card.bottom - ReaderSpace.cardPad)
        }
    }

    private fun countLayout(label: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule
            .onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun overflowDetails(
        label: String,
        layout: TextLayoutResult,
    ): String =
        "$label must fit: size=${layout.size}, lines=${layout.lineCount}, " +
            "paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
            "widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}, " +
            "input=${layout.layoutInput}"

    @Composable
    private fun Grid(
        fontScale: Float,
        longTitles: Boolean = false,
        longCounts: Boolean = false,
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            NextTheme {
                BoardScreen(
                    board = "/g/",
                    description = "Technology",
                    mode = CatalogMode.GRID,
                    itemCount = ROWS.size,
                    rowAt = { index ->
                        ROWS.getOrNull(index)?.let { row ->
                            val counted =
                                if (longCounts && index == 0) {
                                    row.copy(replies = 123_456_789, media = 123_456_789)
                                } else if (longCounts) {
                                    row.copy(unread = 123_456_789)
                                } else {
                                    row
                                }
                            if (longTitles) {
                                counted.copy(
                                    subject = "A complete reader title that needs all three lines",
                                )
                            } else {
                                counted
                            }
                        }
                    },
                    thumbnail = { _, modifier -> Box(modifier) },
                )
            }
        }
    }

    private companion object {
        val LABELS = listOf("1234R 1I", "7 new replies")
        val LONG_LABELS = listOf("123456789R 123456789I", "123456789 new replies")
        val ROWS =
            listOf(
                FeedRow("A title", "/g/", "2m", replies = 1234, media = 1, id = "site/g/1"),
                FeedRow("Another title", "/g/", "1m", replies = 42, media = 1, unread = 7, id = "site/g/2"),
            )
    }
}
