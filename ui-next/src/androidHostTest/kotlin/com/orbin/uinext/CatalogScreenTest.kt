package com.orbin.uinext

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.ReaderLightColors
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The board catalog of docs/design-system: list rows, grid cards, and the rules between them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class CatalogScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `grid columns are the pane width over the minimum card width, rounded down`() {
        assertEquals(3, catalogGridColumns(360.dp))
        assertEquals(3, catalogGridColumns(399.dp))
        assertEquals(3, catalogGridColumns(420.dp))
        assertEquals(5, catalogGridColumns(600.dp))
        assertEquals(2, catalogGridColumns(359.dp))
        assertEquals(1, catalogGridColumns(120.dp))
    }

    @Test
    fun `a pane narrower than one card still shows one column`() {
        assertEquals(1, catalogGridColumns(119.dp))
        assertEquals(1, catalogGridColumns(0.dp))
    }

    @Test
    fun `counts below a thousand are exact and larger ones round down to a tenth of a thousand`() {
        assertEquals(AbbreviatedCount.Exact(0), abbreviatedCount(0))
        assertEquals(AbbreviatedCount.Exact(999), abbreviatedCount(999))
        assertEquals(AbbreviatedCount.Thousands(1, 0), abbreviatedCount(1000))
        assertEquals(AbbreviatedCount.Thousands(1, 2), abbreviatedCount(1299))
        assertEquals(AbbreviatedCount.Thousands(12, 3), abbreviatedCount(12_345))
        assertEquals(AbbreviatedCount.Exact(0), abbreviatedCount(-5))
    }

    @Test
    fun `quoted preview lines take the quote color and other lines the ink color`() {
        val text = previewText("first line\n>quoted line\n\n  >indented quote  \nlast", ReaderLightColors)
        assertEquals("first line\n>quoted line\n>indented quote\nlast", text.text)
        val colors = text.spanStyles.map { text.text.substring(it.start, it.end) to it.item.color }
        assertEquals(
            listOf(
                "first line" to ReaderLightColors.ink,
                ">quoted line" to ReaderLightColors.quote,
                ">indented quote" to ReaderLightColors.quote,
                "last" to ReaderLightColors.ink,
            ),
            colors,
        )
    }

    @Test
    fun `a blank preview produces no text`() {
        assertEquals("", previewText("  \n\n ", ReaderLightColors).text)
    }

    @Test
    fun `a list row is announced as title, replies, then date and opens its thread`() {
        val opened = mutableListOf<String>()
        composeRule.setContent { NextTheme { Catalog(onOpenRow = { opened += it.id }) } }

        composeRule.onNodeWithContentDescription("First thread, 23 replies, 2h").assertIsDisplayed().performClick()

        assertEquals(listOf("g/1"), opened)
    }

    @Test
    fun `a list row with a thumbnail is at least the minimum cell height`() {
        composeRule.setContent { NextTheme { Catalog(thumbnail = true) } }

        composeRule.onAllNodesWithTag(CatalogTags.ROW).onFirst().assertHeightIsAtLeast(ReaderSize.cellMin)
    }

    @Test
    fun `a wide window uses the larger minimum cell height`() {
        composeRule.setContent { NextTheme { Catalog(thumbnail = true, wideWindow = true) } }

        composeRule.onAllNodesWithTag(CatalogTags.ROW).onFirst().assertHeightIsAtLeast(ReaderSize.cellMinWide)
    }

    @Test
    fun `the count pill is the specified height and at least the specified width`() {
        composeRule.setContent { NextTheme { Catalog() } }

        val pill = composeRule.onAllNodesWithTag(CatalogTags.PILL, useUnmergedTree = true).onFirst()
        pill.assertHeightIsEqualTo(ReaderSize.pillHeight)
        pill.assertWidthIsAtLeast(ReaderSize.pillMinWidth)
    }

    @Test
    fun `a watched thread with unread replies is announced by its unread count`() {
        composeRule.setContent { NextTheme { Catalog() } }

        composeRule.onNodeWithContentDescription("Watched thread, 3 new replies, 5m").assertIsDisplayed()
    }

    @Test
    fun `a muted thread hides its subject`() {
        composeRule.setContent { NextTheme { Catalog() } }

        composeRule.onNodeWithContentDescription("Secret subject", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the open thread's row is announced as selected and no other row is`() {
        composeRule.setContent { NextTheme { Catalog(selectedRowId = "g/2") } }

        composeRule
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assertCountEquals(1)
        composeRule
            .onNodeWithContentDescription("Watched thread", substring = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test
    fun `grid mode lays cards out three across in a 360dp pane`() {
        composeRule.setContent { NextTheme { Catalog(mode = CatalogMode.GRID) } }

        val cards = composeRule.onAllNodesWithTag(CatalogTags.CARD)
        val first = cards[0].getUnclippedBoundsInRoot()
        val third = cards[2].getUnclippedBoundsInRoot()
        val fourth = cards[3].getUnclippedBoundsInRoot()
        assertEquals("the first three cards share a row", first.top.value, third.top.value, 0.5f)
        assertTrue("the fourth card starts a new row", fourth.top > first.top)
        assertEquals("the grid starts one gap in", ReaderSpace.gridGap.value, first.left.value, 0.5f)
        // (360 - 4 gaps of 4) / 3 columns
        assertEquals(114.67f, (first.right - first.left).value, 0.5f)
    }

    @Test
    fun `switching between list and grid keeps the scroll position`() {
        val state = LazyGridState()
        var mode by mutableStateOf(CatalogMode.LIST)
        composeRule.setContent { NextTheme { Catalog(mode = mode, state = state, rows = manyRows()) } }

        composeRule.onNodeWithTag(CatalogTags.GRID).performScrollToIndex(12)
        composeRule.waitForIdle()
        assertEquals(12, state.firstVisibleItemIndex)

        mode = CatalogMode.GRID
        composeRule.waitForIdle()

        assertEquals("the same thread leads the grid", 12, state.firstVisibleItemIndex)
    }

    @Test
    fun `at a font scale of 1_3 the pill moves below the text`() {
        composeRule.setContent { Scaled(1.3f) { Catalog(thumbnail = true) } }

        val row = composeRule.onAllNodesWithTag(CatalogTags.ROW).onFirst().getUnclippedBoundsInRoot()
        val pill =
            composeRule.onAllNodesWithTag(CatalogTags.PILL, useUnmergedTree = true).onFirst().getUnclippedBoundsInRoot()
        assertTrue("the pill is in the lower half of the row", pill.top > row.top + (row.bottom - row.top) / 2)
    }

    @Test
    fun `at normal text size the pill sits at the trailing edge of the row`() {
        composeRule.setContent { NextTheme { Catalog(thumbnail = true) } }

        val row = composeRule.onAllNodesWithTag(CatalogTags.ROW).onFirst().getUnclippedBoundsInRoot()
        val pill =
            composeRule.onAllNodesWithTag(CatalogTags.PILL, useUnmergedTree = true).onFirst().getUnclippedBoundsInRoot()
        assertEquals((row.right - ReaderSpace.gutter).value, pill.right.value, 0.5f)
    }

    @Test
    fun `the progress bar shows only while reloading`() {
        var reloading by mutableStateOf(false)
        composeRule.setContent { NextTheme { Catalog(reloading = reloading) } }
        composeRule.onNodeWithTag(CatalogTags.PROGRESS).assertDoesNotExist()

        reloading = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CatalogTags.PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("First thread", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the title opens the board picker only when one is offered`() {
        var picks = 0
        composeRule.setContent { NextTheme { Catalog(onPickBoard = { picks += 1 }) } }

        composeRule.onNodeWithText("Technology").performClick()

        assertEquals(1, picks)
    }

    @Composable
    private fun Scaled(
        fontScale: Float,
        content: @Composable () -> Unit,
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            NextTheme { content() }
        }
    }

    @Composable
    private fun Catalog(
        mode: CatalogMode = CatalogMode.LIST,
        rows: List<FeedRow> = ROWS,
        thumbnail: Boolean = false,
        wideWindow: Boolean = false,
        selectedRowId: String? = null,
        reloading: Boolean = false,
        state: LazyGridState = LazyGridState(),
        onPickBoard: (() -> Unit)? = null,
        onOpenRow: (FeedRow) -> Unit = {},
    ) {
        val slot: (@Composable (FeedRow, Modifier) -> Unit)? = if (thumbnail) { _, _ -> } else null
        BoardScreen(
            board = "/g/",
            description = "Technology",
            itemCount = rows.size,
            rowAt = rows::getOrNull,
            mode = mode,
            wideWindow = wideWindow,
            selectedRowId = selectedRowId,
            reloading = reloading,
            state = state,
            onPickBoard = onPickBoard,
            onOpenRow = onOpenRow,
            thumbnail = slot,
        )
    }

    private fun manyRows() =
        List(40) { index ->
            FeedRow(
                subject = "Thread $index",
                board = "/g/",
                activity = "${index}m",
                replies = index,
                media = 0,
                id = "g/$index",
            )
        }

    private companion object {
        val ROWS =
            listOf(
                FeedRow(subject = "First thread", board = "/g/", activity = "2h", replies = 23, media = 4, id = "g/1"),
                FeedRow(
                    subject = "Watched thread",
                    board = "/g/",
                    activity = "5m",
                    replies = 40,
                    media = 1,
                    id = "g/2",
                    unread = 3,
                ),
                FeedRow(
                    subject = "Secret subject",
                    board = "/g/",
                    activity = "1d",
                    replies = 2,
                    media = 0,
                    id = "g/3",
                    muted = true,
                ),
                FeedRow(
                    subject = "Fourth thread",
                    board = "/g/",
                    activity = "3d",
                    replies = 1200,
                    media = 9,
                    id = "g/4",
                ),
            )
    }
}
