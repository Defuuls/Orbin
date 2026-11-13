package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config

/** The same Card choice must produce full-width catalog cells on both reader entry points. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w390dp-h844dp-mdpi")
class ReaderCardModeTest(
    private val surface: String,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cardLoadingReservesTheLoadedCardWidthAndHorizontalPosition() {
        var loading by mutableStateOf(true)
        compose.setContent { NextTheme { Reader(mode = CatalogMode.CARD, isLoading = loading) } }
        val pending =
            compose
                .onAllNodesWithTag(ReaderStateTags.SKELETON_CELL, useUnmergedTree = true)[0]
                .getUnclippedBoundsInRoot()
        val pane =
            compose
                .onNodeWithTag(if (isHome) gridTag else ReaderStateTags.CATALOG_SKELETON)
                .getUnclippedBoundsInRoot()
        assertEquals("loading uses one 16dp outer gutter", pane.left.value + 16f, pending.left.value, 0.5f)
        assertEquals(pane.right.value - 16f, pending.right.value, 0.5f)

        compose.runOnIdle { loading = false }

        val loaded = card(0).getUnclippedBoundsInRoot()
        assertEquals("loading and loaded cards align", pending.left.value, loaded.left.value, 0.5f)
        assertEquals("loading reserves the full card width", pending.right.value, loaded.right.value, 0.5f)
        compose.onNodeWithTag(ReaderStateTags.CATALOG_SKELETON).assertDoesNotExist()
    }

    @Test
    fun cardSizingUsesASafeFallbackForUnmeasuredOrGutterOnlyWidths() {
        val fallback = CatalogGridSpec(columns = 1, imageHeight = 114.dp)
        listOf((-1).dp, 0.dp, 16.dp, 32.dp, Dp.Infinity, Dp.Unspecified).forEach { width ->
            assertEquals("unsafe pane width $width", fallback, orbinCatalogCardSpec(width))
        }
        assertEquals(CatalogGridSpec(columns = 1, imageHeight = 0.75.dp), orbinCatalogCardSpec(33.dp))
    }

    @Test
    fun cardUsesOneFullWidthColumnAndLargerImagesThanGrid() {
        var mode by mutableStateOf(CatalogMode.GRID)
        compose.setContent { NextTheme { Reader(mode = mode) } }
        val compactCard = card(0).getUnclippedBoundsInRoot()
        val compactImage = thumbnail(0).getUnclippedBoundsInRoot()

        compose.runOnIdle { mode = CatalogMode.CARD }

        val pane = compose.onNodeWithTag(gridTag).getUnclippedBoundsInRoot()
        val first = card(0).getUnclippedBoundsInRoot()
        val second = card(1).getUnclippedBoundsInRoot()
        val image = thumbnail(0).getUnclippedBoundsInRoot()
        val width = (pane.right - pane.left).value - 32f
        assertEquals("16dp outer gutter", pane.left.value + 16f, first.left.value, 0.5f)
        assertEquals("one card fills the available pane", pane.right.value - 16f, first.right.value, 0.5f)
        assertEquals(first.left.value, second.left.value, 0.5f)
        assertEquals(first.right.value, second.right.value, 0.5f)
        assertEquals("8dp between consecutive cards", first.bottom.value + 8f, second.top.value, 0.5f)
        assertEquals("full-size card image height", width * 0.75f, (image.bottom - image.top).value, 0.5f)
        assertEquals("12dp image inset", first.left.value + 12f, image.left.value, 0.5f)
        assertEquals(first.right.value - 12f, image.right.value, 0.5f)
        assertTrue("Card is wider than Grid", first.right - first.left > compactCard.right - compactCard.left)
        assertTrue("Card image is taller than Grid", image.bottom - image.top > compactImage.bottom - compactImage.top)
    }

    @Test
    fun overflowOffersBothOtherModesAndDirectlySelectsCardOrGrid() {
        var mode by mutableStateOf(CatalogMode.LIST)
        val choices = mutableListOf<CatalogMode>()
        compose.setContent {
            NextTheme {
                Reader(mode = mode, onModeChange = {
                    choices += it
                    mode = it
                })
            }
        }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("List view").assertDoesNotExist()
        compose.onNodeWithText("Grid view").assertIsDisplayed()
        compose.onNodeWithText("Card view").performClick()
        compose.runOnIdle { assertEquals(CatalogMode.CARD, mode) }
        card(0).assertIsDisplayed()

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Card view").assertDoesNotExist()
        compose.onNodeWithText("List view").assertIsDisplayed()
        compose.onNodeWithText("Grid view").performClick()
        compose.runOnIdle {
            assertEquals(CatalogMode.GRID, mode)
            assertEquals(listOf(CatalogMode.CARD, CatalogMode.GRID), choices)
        }
    }

    @Test
    fun cardKeepsTheExactThreadAndMediaTargets() {
        val openedThreads = mutableListOf<FeedRow>()
        val openedMedia = mutableListOf<FeedRow>()
        compose.setContent {
            NextTheme {
                Reader(
                    mode = CatalogMode.CARD,
                    onOpenRow = { openedThreads += it },
                    onOpenMedia = { openedMedia += it },
                )
            }
        }
        if (isHome) {
            thumbnail(0).performTouchInput { click(center) }
            compose.runOnIdle {
                assertEquals(listOf(ROWS[0]), openedMedia)
                assertTrue(openedThreads.isEmpty())
            }
        } else {
            card(0).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
            card(1).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        }
        card(1).performClick()
        compose.runOnIdle { assertEquals(listOf(ROWS[1]), openedThreads) }
    }

    @Test
    @Config(qualifiers = "en-rUS-w248dp-h800dp-mdpi")
    fun narrowCardAtMaximumTextKeepsContentInsideThePaneAndModesReachable() {
        var mode by mutableStateOf(CatalogMode.CARD)
        compose.setContent { NextTheme(fontScale = 2f) { Reader(mode, onModeChange = { mode = it }) } }
        val pane = compose.onNodeWithTag(gridTag).getUnclippedBoundsInRoot()
        val first = card(0).getUnclippedBoundsInRoot()
        val image = thumbnail(0).getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText(ROWS[0].subject, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val counts = compose.onAllNodesWithTag(CatalogTags.COUNTS, useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        assertTrue(first.left >= pane.left && first.right <= pane.right)
        assertTrue(title.left >= first.left && title.right <= first.right)
        assertTrue("the enlarged title stays above the image", title.bottom <= image.top)
        assertTrue("counts stay below the image", counts.top >= image.bottom)
        assertTrue(counts.left >= first.left && counts.right <= first.right)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Grid view").assertIsDisplayed()
        compose.onNodeWithText("List view").performClick()
        compose.runOnIdle { assertEquals(CatalogMode.LIST, mode) }
    }

    private val isHome get() = surface == "Home"
    private val gridTag get() = if (isHome) ReaderFeedTags.GRID else CatalogTags.GRID

    private fun card(index: Int) =
        if (isHome) {
            compose.onNodeWithTag(ReaderFeedTags.row(ROWS[index].id))
        } else {
            compose.onAllNodesWithTag(CatalogTags.CARD)[index]
        }

    private fun thumbnail(index: Int) =
        if (isHome) {
            compose.onNodeWithTag(ReaderFeedTags.thumbnail(ROWS[index].id), useUnmergedTree = true)
        } else {
            compose.onAllNodesWithTag(CatalogTags.THUMBNAIL, useUnmergedTree = true)[index]
        }

    @Composable
    private fun Reader(
        mode: CatalogMode,
        onModeChange: ((CatalogMode) -> Unit)? = null,
        onOpenRow: (FeedRow) -> Unit = {},
        onOpenMedia: (FeedRow) -> Unit = {},
        isLoading: Boolean = false,
    ) {
        if (isHome) {
            FeedScreen(
                rows = if (isLoading) emptyList() else ROWS,
                mode = mode,
                isLoading = isLoading,
                onModeChange = onModeChange,
                onOpenRow = onOpenRow,
                onOpenMedia = onOpenMedia,
                thumbnail = { _, modifier -> Box(modifier) },
            )
        } else {
            BoardScreen(
                board = "/g/",
                description = "Technology",
                itemCount = ROWS.size,
                rowAt = ROWS::getOrNull,
                mode = mode,
                onModeChange = onModeChange,
                onOpenRow = onOpenRow,
                selectedRowId = ROWS[1].id,
                thumbnail = { _, modifier -> Box(modifier) },
                stateContent = if (isLoading) ({ ReaderCatalogSkeleton(mode = mode) }) else null,
            )
        }
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun surfaces(): List<Array<String>> = listOf("Home", "Board").map { arrayOf(it) }

        private val ROWS =
            listOf(
                FeedRow(
                    subject = "First thread",
                    board = "/g/",
                    activity = "2h",
                    replies = 23,
                    media = 4,
                    id = "fourchan/g/101",
                    site = "4chan",
                    mediaLabel = "WEBM",
                ),
                FeedRow(
                    subject = "Second thread",
                    board = "/g/",
                    activity = "5m",
                    replies = 40,
                    media = 2,
                    id = "fourchan/g/202",
                    site = "4chan",
                    mediaLabel = "JPG",
                ),
            )
    }
}
