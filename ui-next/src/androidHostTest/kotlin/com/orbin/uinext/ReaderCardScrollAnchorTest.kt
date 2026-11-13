package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A deep Card offset must not skip the retained thread when its replacement row is shorter. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h1000dp-mdpi")
class ReaderCardScrollAnchorTest(
    private val surface: Surface,
    private val targetMode: CatalogMode,
) {
    enum class Surface { HOME, BOARD }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun changingPresentationKeepsTheLeadingThreadAndResetsItsCardOffset() {
        var mode by mutableStateOf(CatalogMode.CARD)
        val rows = cardAnchorRows()
        val boardState = LazyGridState()
        compose.setContent {
            NextTheme {
                Box(Modifier.requiredSize(800.dp, 1000.dp)) {
                    when (surface) {
                        Surface.HOME ->
                            FeedScreen(
                                rows = rows,
                                mode = mode,
                                wideWindow = true,
                                thumbnail = { _, modifier -> Box(modifier) },
                            )
                        Surface.BOARD ->
                            BoardScreen(
                                board = "/g/",
                                description = "Technology",
                                itemCount = rows.size,
                                rowAt = { rows.getOrNull(it) },
                                mode = mode,
                                wideWindow = true,
                                state = boardState,
                                thumbnail = { _, modifier -> Box(modifier) },
                            )
                    }
                }
            }
        }

        grid().performScrollToIndex(ANCHOR_INDEX)
        grid().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, CARD_OFFSET_PX) }
        compose.waitForIdle()
        val before = anchor(rows, CatalogMode.CARD).assertIsDisplayed().getUnclippedBoundsInRoot()
        val viewport = grid().getUnclippedBoundsInRoot()
        assertTrue("The fixture must scroll deeply within the same full-width card", before.top < viewport.top - 450.dp)
        assertTrue("The retained card must still reach into the viewport", before.bottom > viewport.top)
        if (surface == Surface.BOARD) {
            compose.runOnIdle {
                assertEquals(ANCHOR_INDEX, boardState.firstVisibleItemIndex)
                assertTrue(boardState.firstVisibleItemScrollOffset >= CARD_OFFSET_PX.toInt())
            }
        }

        compose.runOnIdle { mode = targetMode }
        val after = anchor(rows, targetMode).assertIsDisplayed().getUnclippedBoundsInRoot()
        val afterViewport = grid().getUnclippedBoundsInRoot()
        val topOffset = (after.top - afterViewport.top).value
        assertTrue(
            "The same thread must lead the new layout, with only its content gutter before it: $topOffset",
            topOffset >= -1f && topOffset <= ORBIN_CATALOG_GUTTER.value + 1f,
        )
        if (surface == Surface.BOARD) {
            compose.runOnIdle {
                assertEquals(ANCHOR_INDEX, boardState.firstVisibleItemIndex)
                assertEquals(0, boardState.firstVisibleItemScrollOffset)
            }
        }
    }

    private fun grid(): SemanticsNodeInteraction =
        compose.onNodeWithTag(if (surface == Surface.HOME) ReaderFeedTags.GRID else CatalogTags.GRID)

    private fun anchor(
        rows: List<FeedRow>,
        mode: CatalogMode,
    ): SemanticsNodeInteraction =
        when (surface) {
            Surface.HOME -> compose.onNodeWithTag(ReaderFeedTags.row(rows[ANCHOR_INDEX].id))
            Surface.BOARD ->
                compose.onNode(
                    hasTestTag(if (mode == CatalogMode.LIST) CatalogTags.ROW else CatalogTags.CARD) and
                        hasContentDescription(rows[ANCHOR_INDEX].subject, substring = true),
                )
        }

    companion object {
        private const val ANCHOR_INDEX = 8
        private const val CARD_OFFSET_PX = 500f

        @JvmStatic
        @Parameters(name = "{0} Card to {1}")
        fun presentations(): List<Array<Any>> =
            Surface.entries.flatMap { surface ->
                listOf(CatalogMode.GRID, CatalogMode.LIST).map { target -> arrayOf<Any>(surface, target) }
            }
    }
}

/** Entering a presentation with restored state must leave its valid pixel offset intact. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h1000dp-mdpi")
class ReaderCardRestoredScrollAnchorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun hiddenHomeSourceRestoresItsThreadWithoutThePreviousCardOffsetAfterModeChanges() {
        var source by mutableIntStateOf(0)
        var mode by mutableStateOf(CatalogMode.CARD)
        val first = cardAnchorRows()
        val second = cardAnchorRows().map { it.copy(id = "second/${it.id}", subject = "Second ${it.subject}") }
        compose.setContent {
            NextTheme {
                Box(Modifier.requiredSize(800.dp, 1000.dp)) {
                    FeedScreen(
                        rows = if (source == 0) first else second,
                        sourceLabels = listOf("First source", "Second source"),
                        selectedSource = source,
                        onSelectSource = { source = it },
                        mode = mode,
                        wideWindow = true,
                        thumbnail = { _, modifier -> Box(modifier) },
                    )
                }
            }
        }
        compose.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(8)
        compose.onNodeWithTag(ReaderFeedTags.GRID).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 500f) }
        compose.waitForIdle()
        val before =
            compose
                .onNodeWithTag(ReaderFeedTags.row(first[8].id))
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag(ReaderFeedTags.GRID).getUnclippedBoundsInRoot()
        assertTrue("The source must save a deep Card offset", before.top < viewport.top - 450.dp)

        compose.runOnIdle { source = 1 }
        compose.onNodeWithTag(ReaderFeedTags.row(second[0].id)).assertIsDisplayed()
        compose.runOnIdle { mode = CatalogMode.GRID }
        compose.onNodeWithTag(ReaderFeedTags.row(second[0].id)).assertIsDisplayed()
        compose.runOnIdle { source = 0 }

        val after =
            compose
                .onNodeWithTag(ReaderFeedTags.row(first[8].id))
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
        val afterViewport = compose.onNodeWithTag(ReaderFeedTags.GRID).getUnclippedBoundsInRoot()
        val topOffset = (after.top - afterViewport.top).value
        assertTrue(
            "Restoring the hidden source must retain its thread and reset the obsolete Card offset: $topOffset",
            topOffset >= -1f && topOffset <= ORBIN_CATALOG_GUTTER.value + 1f,
        )
    }

    @Test
    fun unchangedCardModeKeepsItsInitialIndexAndOffsetAcrossRecomposition() {
        val state = LazyGridState(firstVisibleItemIndex = 4, firstVisibleItemScrollOffset = 37)
        val rows = cardAnchorRows()
        var description by mutableStateOf("Technology")
        compose.setContent {
            NextTheme {
                Box(Modifier.requiredSize(800.dp, 1000.dp)) {
                    BoardScreen(
                        board = "/g/",
                        description = description,
                        itemCount = rows.size,
                        rowAt = { rows.getOrNull(it) },
                        mode = CatalogMode.CARD,
                        wideWindow = true,
                        state = state,
                        thumbnail = { _, modifier -> Box(modifier) },
                    )
                }
            }
        }
        compose.runOnIdle {
            assertEquals(4, state.firstVisibleItemIndex)
            assertEquals(37, state.firstVisibleItemScrollOffset)
            description = "Technology catalog"
        }
        compose.runOnIdle {
            assertEquals(4, state.firstVisibleItemIndex)
            assertEquals(37, state.firstVisibleItemScrollOffset)
        }
    }
}

private fun cardAnchorRows(): List<FeedRow> =
    List(40) { index ->
        FeedRow(
            id = "fourchan/g/$index",
            subject = "Anchor thread $index",
            board = "/g/",
            activity = "1m",
            replies = 12,
            media = 2,
            excerpt = "A preview for the retained reading position.",
        )
    }
