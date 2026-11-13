package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
    fun `thread files columns are the pane width over the minimum card width, rounded down`() {
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
    fun `both card layouts ask hosts for a sharper image than the provider thumbnail`() {
        assertTrue(CatalogMode.GRID.sharpensThumbnails)
        assertTrue(CatalogMode.CARD.sharpensThumbnails)
        assertTrue(!CatalogMode.LIST.sharpensThumbnails)
        assertEquals(listOf(CatalogMode.LIST, CatalogMode.GRID, CatalogMode.CARD), CatalogMode.entries)
    }

    @Test
    fun `a portrait phone grid is one column of images half the viewport tall`() {
        assertEquals(CatalogGridSpec(columns = 1, imageHeight = 370.dp), catalogGridSpec(360.dp, 740.dp))
        assertEquals(CatalogGridSpec(columns = 1, imageHeight = 600.dp), catalogGridSpec(800.dp, 1200.dp))
    }

    @Test
    fun `a wide viewport splits into columns no narrower than the image is tall`() {
        assertEquals(CatalogGridSpec(columns = 4, imageHeight = 180.dp), catalogGridSpec(740.dp, 360.dp))
        assertEquals(CatalogGridSpec(columns = 3, imageHeight = 400.dp), catalogGridSpec(1200.dp, 800.dp))
        // A square viewport is exactly two images wide; one dp narrower rounds down to one column.
        assertEquals(2, catalogGridSpec(800.dp, 800.dp).columns)
        assertEquals(1, catalogGridSpec(799.dp, 800.dp).columns)
    }

    @Test
    fun `rotating a window keeps the image at half of whichever height it has`() {
        val portrait = catalogGridSpec(411.dp, 800.dp)
        val landscape = catalogGridSpec(800.dp, 411.dp)
        assertEquals(CatalogGridSpec(columns = 1, imageHeight = 400.dp), portrait)
        assertEquals(CatalogGridSpec(columns = 3, imageHeight = 205.5.dp), landscape)
    }

    @Test
    fun `a short viewport never shrinks the image below the minimum card size`() {
        assertEquals(ReaderSize.gridCardMin, catalogGridSpec(360.dp, 200.dp).imageHeight)
        assertEquals(ReaderSize.gridCardMin, catalogGridSpec(360.dp, 240.dp).imageHeight)
        assertEquals(121.dp, catalogGridSpec(360.dp, 242.dp).imageHeight)
        // The floor also sets the columns: 360 / 120.
        assertEquals(3, catalogGridSpec(360.dp, 200.dp).columns)
    }

    @Test
    fun `an unmeasured, negative or unbounded viewport falls back to one safe column`() {
        val fallback = CatalogGridSpec(columns = 1, imageHeight = ReaderSize.gridCardMin)
        assertEquals(fallback, catalogGridSpec(0.dp, 0.dp))
        assertEquals(fallback, catalogGridSpec((-1).dp, (-1).dp))
        assertEquals(fallback, catalogGridSpec(119.dp, Dp.Infinity))
        assertEquals(fallback, catalogGridSpec(Dp.Infinity, Dp.Unspecified))
        assertEquals(fallback.copy(columns = 3), catalogGridSpec(360.dp, Dp.Infinity))
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

        composeRule
            .onNodeWithContentDescription(
                "First thread, 23 replies, 4 files, 2h",
            ).assertIsDisplayed()
            .performClick()

        assertEquals(listOf("g/1"), opened)
    }

    @Test
    fun `a list row keeps the full 72dp thumbnail without an outer inset`() {
        composeRule.setContent { NextTheme { Catalog(thumbnail = true) } }

        val thumbnail = composeRule.onAllNodesWithTag(CatalogTags.THUMBNAIL, true).onFirst()
        thumbnail.assertHeightIsAtLeast(72.dp)
        assertEquals(0f, thumbnail.getUnclippedBoundsInRoot().left.value, 0.5f)
    }

    @Test
    fun `a wide window uses the full 100dp thumbnail without an outer inset`() {
        composeRule.setContent { NextTheme { Catalog(thumbnail = true, wideWindow = true) } }

        val thumbnail = composeRule.onAllNodesWithTag(CatalogTags.THUMBNAIL, true).onFirst()
        thumbnail.assertHeightIsAtLeast(100.dp)
        assertEquals(0f, thumbnail.getUnclippedBoundsInRoot().left.value, 0.5f)
    }

    @Test
    fun `catalog counters are plain reply and file metadata without a trailing pill`() {
        composeRule.setContent { NextTheme { Catalog() } }

        composeRule.onNodeWithTag(CatalogTags.PILL, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("23 replies, 4 files", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("3 new replies", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a watched thread with unread replies is announced by its unread count`() {
        composeRule.setContent { NextTheme { Catalog() } }

        composeRule
            .onNodeWithContentDescription(
                "Watched thread, 40 replies, 1 file, 3 new replies, 5m",
            ).assertIsDisplayed()
    }

    @Test
    fun `a muted thread hides its subject`() {
        composeRule.setContent { NextTheme { Catalog() } }

        composeRule.onNodeWithContentDescription("Secret subject", substring = true).assertDoesNotExist()
    }

    @Test
    fun `sticky and closed titles carry their status icons`() {
        composeRule.setContent {
            NextTheme { Catalog(rows = listOf(ROWS.first().copy(sticky = true, closed = true))) }
        }
        composeRule.onNodeWithTag("catalog-sticky", true).assertExists()
        composeRule.onNodeWithTag("catalog-closed", true).assertExists()
    }

    @Test
    fun `a catalog spoiler takes the first thumbnail tap without composing media or opening its thread`() {
        var mediaCompositions = 0
        var opens = 0
        composeRule.setContent {
            NextTheme {
                BoardScreen(
                    board = "/g/",
                    description = "Technology",
                    itemCount = 1,
                    rowAt = { ROWS.first().copy(id = "source/g/1", imageSpoiler = true) },
                    onOpenRow = { opens++ },
                    thumbnail = { _, modifier ->
                        SideEffect { mediaCompositions++ }
                        Box(modifier.testTag("requested-thumbnail"))
                    },
                )
            }
        }
        composeRule.onAllNodesWithContentDescription("First thread, 23 replies, 4 files, 2h").assertCountEquals(1)
        composeRule.runOnIdle { assertEquals(0, mediaCompositions) }
        composeRule.onNodeWithTag("requested-thumbnail", true).assertDoesNotExist()

        composeRule.onNodeWithTag(CatalogTags.THUMBNAIL, true).performTouchInput { click(center) }

        composeRule.runOnIdle {
            assertEquals(0, opens)
            assertTrue("No provider thumbnail can be requested before this composition", mediaCompositions > 0)
        }
        composeRule.onNodeWithTag(CatalogTags.THUMBNAIL, true).performTouchInput { click(center) }
        composeRule.runOnIdle { assertEquals(1, opens) }
    }

    @Test
    fun `a grid card exposes reveal as a custom row action while keeping one announcement`() {
        var mediaCompositions = 0
        var opens = 0
        composeRule.setContent {
            NextTheme {
                BoardScreen(
                    board = "/g/",
                    description = "Technology",
                    mode = CatalogMode.GRID,
                    itemCount = 1,
                    rowAt = { ROWS.first().copy(id = "source/g/1", imageSpoiler = true) },
                    onOpenRow = { opens++ },
                    thumbnail = { _, modifier ->
                        SideEffect { mediaCompositions++ }
                        Box(modifier.testTag("requested-thumbnail"))
                    },
                )
            }
        }
        val card = composeRule.onNodeWithTag(CatalogTags.CARD)
        composeRule.onAllNodesWithContentDescription("First thread, 23 replies, 4 files, 2h").assertCountEquals(1)
        composeRule.runOnIdle { assertEquals(0, mediaCompositions) }
        val reveal = card.fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        assertEquals("Reveal spoiler", reveal.label)
        composeRule.runOnIdle { assertTrue(reveal.action()) }

        composeRule.runOnIdle {
            assertEquals(0, opens)
            assertTrue(mediaCompositions > 0)
        }
        card.assert(
            SemanticsMatcher("no reveal action remains") { !it.config.contains(SemanticsActions.CustomActions) },
        )
        card.performClick()
        composeRule.runOnIdle { assertEquals(1, opens) }
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
    fun `grid mode shows two compact cards with inset images on a phone`() {
        composeRule.setContent { NextTheme { Catalog(mode = CatalogMode.GRID, thumbnail = true) } }

        val grid = composeRule.onNodeWithTag(CatalogTags.GRID).getUnclippedBoundsInRoot()
        val expected = orbinCatalogGridSpec(grid.right - grid.left)
        val cards = composeRule.onAllNodesWithTag(CatalogTags.CARD)
        val first = cards[0].getUnclippedBoundsInRoot()
        val second = cards[1].getUnclippedBoundsInRoot()
        val image =
            composeRule.onAllNodesWithTag(CatalogTags.THUMBNAIL, useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        assertEquals("two columns on a phone", 2, expected.columns)
        assertEquals("the outer gutter is 16dp", ORBIN_CATALOG_GUTTER.value, first.left.value, 0.5f)
        assertEquals("two cards share the first line", first.top.value, second.top.value, 0.5f)
        val cardWidth = (grid.right - grid.left - ORBIN_CATALOG_GUTTER * 2 - ORBIN_CATALOG_GAP).value / 2f
        assertEquals("cards share the pane equally", cardWidth, (first.right - first.left).value, 0.5f)
        assertEquals(
            "the image follows the grid spec",
            expected.imageHeight.value,
            (image.bottom - image.top).value,
            0.5f,
        )
        assertEquals("the image is inset 12dp", first.left.value + 12f, image.left.value, 0.5f)
        assertTrue("counts sit below the image", first.bottom > image.bottom)
        composeRule.onNodeWithText("23 replies", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("4 files", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a short pane still shows two Coral catalog columns`() {
        composeRule.setContent {
            NextTheme { Box(Modifier.size(360.dp, 356.dp)) { Catalog(mode = CatalogMode.GRID, thumbnail = true) } }
        }

        val grid = composeRule.onNodeWithTag(CatalogTags.GRID).getUnclippedBoundsInRoot()
        val expected = orbinCatalogGridSpec(grid.right - grid.left)
        assertEquals("360dp holds two Coral cards across", 2, expected.columns)
        val cards = composeRule.onAllNodesWithTag(CatalogTags.CARD)
        val first = cards[0].getUnclippedBoundsInRoot()
        val second = cards[1].getUnclippedBoundsInRoot()
        val image =
            composeRule.onAllNodesWithTag(CatalogTags.THUMBNAIL, useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        assertEquals("two cards share the first line", first.top.value, second.top.value, 0.5f)
        val cardWidth = (grid.right - grid.left - ORBIN_CATALOG_GUTTER * 2 - ORBIN_CATALOG_GAP).value / 2f
        assertEquals(cardWidth, (first.right - first.left).value, 0.5f)
        assertEquals(expected.imageHeight.value, (image.bottom - image.top).value, 0.5f)
    }

    @Test
    fun `a pending grid row reserves the same image height as a loaded card`() {
        composeRule.setContent {
            NextTheme {
                BoardScreen(
                    board = "/g/",
                    description = "Technology",
                    itemCount = 2,
                    rowAt = { index -> ROWS.first().takeIf { index == 0 } },
                    mode = CatalogMode.GRID,
                    thumbnail = { _, _ -> },
                )
            }
        }

        val image =
            composeRule.onNodeWithTag(CatalogTags.THUMBNAIL, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val pending = composeRule.onNodeWithTag(CatalogTags.PENDING).getUnclippedBoundsInRoot()
        val grid = composeRule.onNodeWithTag(CatalogTags.GRID).getUnclippedBoundsInRoot()
        val expected = orbinCatalogGridSpec(grid.right - grid.left)
        assertEquals(expected.imageHeight.value, (image.bottom - image.top).value, 0.5f)
        assertEquals(
            "the pending cell uses the same image height",
            expected.imageHeight.value,
            (pending.bottom - pending.top).value,
            0.5f,
        )
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
    fun `at a font scale of 1_3 plain counts remain below the title`() {
        composeRule.setContent { Scaled(1.3f) { Catalog(thumbnail = true) } }

        val title = composeRule.onNodeWithText("First thread", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val counts =
            composeRule
                .onNodeWithText(
                    "23 replies, 4 files",
                    useUnmergedTree = true,
                ).getUnclippedBoundsInRoot()
        assertTrue(counts.top >= title.bottom)
        assertEquals(title.left.value, counts.left.value, 0.5f)
    }

    @Test
    fun `at normal text size counts share the title column instead of reserving a trailing badge`() {
        composeRule.setContent { NextTheme { Catalog(thumbnail = true) } }

        val title = composeRule.onNodeWithText("First thread", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val counts =
            composeRule
                .onNodeWithText(
                    "23 replies, 4 files",
                    useUnmergedTree = true,
                ).getUnclippedBoundsInRoot()
        assertEquals(title.left.value, counts.left.value, 0.5f)
        assertTrue(counts.top >= title.bottom)
    }

    @Test
    fun `a row without an image gives its full thumbnail width back to the text`() {
        val rows =
            listOf(
                ROWS.first().copy(subject = "Image row", excerpt = "Preview"),
                ROWS.first().copy(subject = "Text row", hasPreview = false, id = "other", excerpt = "Preview"),
            )
        composeRule.setContent { NextTheme { Catalog(rows = rows, thumbnail = true) } }
        val imageText = composeRule.onNodeWithText("Image row", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val text = composeRule.onNodeWithText("Text row", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(8f, text.left.value, 0.5f)
        assertEquals(ReaderSize.thumbnail.value, (imageText.left - text.left).value, 0.5f)
        composeRule.onAllNodesWithTag(CatalogTags.THUMBNAIL, true).assertCountEquals(1)
    }

    @Test
    fun `rtl mirrors the catalog thumbnail and count column while retaining selected semantics`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NextTheme { Catalog(rows = ROWS.take(1), thumbnail = true, selectedRowId = "g/1") }
            }
        }
        val image = composeRule.onNodeWithTag(CatalogTags.THUMBNAIL, true).getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithText("First thread", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val counts =
            composeRule
                .onNodeWithText(
                    "23 replies, 4 files",
                    useUnmergedTree = true,
                ).getUnclippedBoundsInRoot()
        assertEquals(360f, image.right.value, 0.5f)
        assertEquals(title.right.value, counts.right.value, 0.5f)
        composeRule
            .onNodeWithTag(CatalogTags.ROW)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test
    fun `font two puts the thumbnail above the preview and keeps complete counts in the text column`() {
        composeRule.setContent { Scaled(2f) { Catalog(rows = ROWS.take(1), thumbnail = true) } }
        val image = composeRule.onNodeWithTag(CatalogTags.THUMBNAIL, true).getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithText("First thread", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val counts =
            composeRule
                .onNodeWithText(
                    "23 replies, 4 files",
                    useUnmergedTree = true,
                ).getUnclippedBoundsInRoot()
        assertTrue(title.top >= image.bottom + ReaderSpace.gapSmall)
        assertEquals(title.left.value, counts.left.value, 0.5f)
        assertTrue(counts.top >= title.bottom)
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

        composeRule.onNodeWithText("/g/ - Technology").performClick()

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
