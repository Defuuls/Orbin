package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The production Home must use reader rows, with playback confined to the explicit media route. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class ReaderHomePresentationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun readerHomeStartsAsRowsWithoutPagerOrActionRail() {
        val rows = rows("fourchan")
        compose.setContent {
            NextTheme {
                FeedScreen(rows = rows, onOpenBoards = {}, onOpenSearch = {}, onSettings = {})
            }
        }
        compose.onNodeWithTag(ReaderAppBarTags.BAR).assertIsDisplayed()
        compose.onNodeWithTag(ReaderFeedTags.GRID).assertIsDisplayed()
        compose.onNodeWithTag(ReaderFeedTags.row(rows[0].id)).assertIsDisplayed()
        compose.onNodeWithTag(ReaderFeedTags.row(rows[1].id)).assertIsDisplayed()
        compose.onNodeWithTag(TikTokFeedTags.PAGER).assertDoesNotExist()
        compose.onNodeWithTag(TikTokFeedTags.ACTIONS).assertDoesNotExist()
        compose.onNodeWithTag(TikTokFeedTags.HEADER).assertDoesNotExist()
        assertNoFeedSelectors()
    }

    @Test
    fun previewsStayInactiveUntilExplicitMediaOpen() {
        val active = mutableListOf<Boolean>()
        val owners = mutableListOf<String?>()
        val upcoming = mutableListOf<String?>()
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = rows("fourchan"),
                    onActivePreviewChanged = { owners += it },
                    onUpcomingPreviewChanged = { upcoming += it },
                    mediaContent = { _, modifier, playing ->
                        active += playing
                        Box(modifier)
                    },
                )
            }
        }
        compose.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(18)
        compose.runOnIdle {
            assertTrue("The fallback preview must actually be exercised", active.isNotEmpty())
            assertFalse("Scrolling Home must never start a media player", active.any { it })
            assertTrue("Home releases active preview ownership", owners.isNotEmpty() && owners.all { it == null })
            assertTrue(
                "Home does not preload an implicit next video",
                upcoming.isNotEmpty() && upcoming.all { it == null },
            )
        }
    }

    @Test
    fun readerModesRetainIndependentSourcePositions() {
        var source by mutableIntStateOf(0)
        var mode by mutableStateOf(CatalogMode.LIST)
        val first = rows("fourchan")
        val second = rows("bbwchan")
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = if (source == 0) first else second,
                    sourceLabels = listOf("4chan", "BBWChan"),
                    selectedSource = source,
                    onSelectSource = { source = it },
                    mode = mode,
                    onModeChange = { mode = it },
                    onOpenBoards = {},
                )
            }
        }
        compose.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(18)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Grid view").performClick()
        compose.onNodeWithTag(ReaderFeedTags.row(first[18].id)).assertIsDisplayed()
        selectSource("BBWChan")
        compose.onNodeWithTag(ReaderFeedTags.row(second[0].id)).assertIsDisplayed()
        compose.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(9)
        selectSource("4chan")
        compose.onNodeWithTag(ReaderFeedTags.row(first[18].id)).assertIsDisplayed()
        selectSource("BBWChan")
        compose.onNodeWithTag(ReaderFeedTags.row(second[9].id)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(CatalogMode.GRID, mode) }
    }

    @Test
    fun sourceSelectionRetainsItsOwnPosition() {
        var source by mutableIntStateOf(0)
        val first = rows("fourchan")
        val second = rows("bbwchan")
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = if (source == 0) first else second,
                    sourceLabels = listOf("4chan", "BBWChan"),
                    selectedSource = source,
                    onSelectSource = { source = it },
                )
            }
        }
        compose.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(18)
        compose.onNodeWithContentDescription("Choose source").performClick()
        compose.onNode(hasText("BBWChan") and radioButton()).performClick()
        compose.onNodeWithTag(ReaderFeedTags.row(second[0].id)).assertIsDisplayed()
        compose.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(9)
        compose.onNodeWithContentDescription("Choose source").performClick()
        compose.onNode(hasText("4chan") and radioButton()).performClick()
        compose.onNodeWithTag(ReaderFeedTags.row(first[18].id)).assertIsDisplayed()
    }

    @Test
    fun reorderingRetainsTheFirstVisibleThread() {
        var rows by mutableStateOf(rows("fourchan"))
        compose.setContent { NextTheme { FeedScreen(rows = rows) } }
        compose.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(18)
        val retained = rows[18]
        val before = compose.onNodeWithTag(ReaderFeedTags.row(retained.id)).getUnclippedBoundsInRoot()
        compose.runOnIdle { rows = listOf(row("new", 0)) + rows }
        val after =
            compose
                .onNodeWithTag(
                    ReaderFeedTags.row(retained.id),
                ).assertIsDisplayed()
                .getUnclippedBoundsInRoot()
        assertEquals("Inserting another source must retain the reading anchor", before.top.value, after.top.value, 0.5f)
    }

    @Test
    fun hiddenSourceReorderingRetainsTheThreadAndPixelOffsetOnReturn() {
        var source by mutableIntStateOf(0)
        var first by mutableStateOf(rows("fourchan"))
        val second = rows("bbwchan")
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = if (source == 0) first else second,
                    sourceLabels = listOf("4chan", "BBWChan"),
                    selectedSource = source,
                    onSelectSource = { source = it },
                )
            }
        }
        compose.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(18)
        compose.onNodeWithTag(ReaderFeedTags.GRID).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 17f) }
        compose.waitForIdle()
        val retained = ReaderFeedTags.row(first[18].id)
        val before = compose.onNodeWithTag(retained).getUnclippedBoundsInRoot()
        val grid = compose.onNodeWithTag(ReaderFeedTags.GRID).getUnclippedBoundsInRoot()
        assertTrue("The fixture must retain a nonzero pixel offset", before.top < grid.top)
        selectSource("BBWChan")
        compose.onNodeWithTag(ReaderFeedTags.row(second[0].id)).assertIsDisplayed()
        compose.runOnIdle { first = listOf(row("new-subscribed-board", 100)) + first }
        selectSource("4chan")
        val after = compose.onNodeWithTag(retained).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(
            "Returning must restore thread identity instead of the obsolete index",
            before.top.value,
            after.top.value,
            0.5f,
        )
    }

    @Test
    fun removingHomeClearsPreviewOwnership() {
        var visible by mutableStateOf(true)
        val owners = mutableListOf<String?>()
        val upcoming = mutableListOf<String?>()
        compose.setContent {
            NextTheme {
                if (visible) {
                    FeedScreen(
                        rows = rows("fourchan"),
                        onActivePreviewChanged = { owners += it },
                        onUpcomingPreviewChanged = { upcoming += it },
                    )
                }
            }
        }
        compose.runOnIdle { visible = false }
        compose.runOnIdle {
            assertTrue(owners.isNotEmpty() && owners.all { it == null })
            assertTrue(upcoming.isNotEmpty() && upcoming.all { it == null })
        }
    }

    @Test
    fun completedCachedBatchesStillRequestMore() {
        var completedBatch by mutableIntStateOf(8)
        var calls = 0
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = if (completedBatch >= 24) rows("fourchan") else rows("fourchan").take(2),
                    loadMoreKey = completedBatch,
                    onLoadMore =
                        if (completedBatch >= 24) {
                            null
                        } else {
                            {
                                calls++
                                completedBatch += 8
                            }
                        },
                )
            }
        }
        compose.waitUntil { calls == 2 }
        compose.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(18)
        compose.onNodeWithTag(ReaderFeedTags.row(row("fourchan", 18).id)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(24, completedBatch) }
    }

    @Test
    fun asynchronousReturnToSourceRetainsReadingAnchor() {
        var source by mutableIntStateOf(0)
        var firstReady by mutableStateOf(true)
        val first = rows("fourchan")
        val second = rows("bbwchan")
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows =
                        when {
                            source == 1 -> second
                            firstReady -> first
                            else -> emptyList()
                        },
                    sourceLabels = listOf("4chan", "BBWChan"),
                    selectedSource = source,
                    onSelectSource = {
                        if (it == 0) firstReady = false
                        source = it
                    },
                    isLoading = source == 0 && !firstReady,
                )
            }
        }
        compose.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(20)
        val retained = ReaderFeedTags.row(first[20].id)
        val before = compose.onNodeWithTag(retained).getUnclippedBoundsInRoot()
        selectSource("BBWChan")
        compose.onNodeWithTag(ReaderFeedTags.row(second[0].id)).assertIsDisplayed()
        selectSource("4chan")
        compose.onNodeWithTag(retained).assertDoesNotExist()
        compose.waitForIdle()
        compose.runOnIdle { firstReady = true }
        compose.waitForIdle()
        val after = compose.onNodeWithTag(retained).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(
            "The loading placeholder must not replace the saved thread anchor",
            before.top.value,
            after.top.value,
            0.5f,
        )
    }

    @Test
    fun sourceChoiceRemainsReachableDuringLoadingAndSourceFailure() {
        var source by mutableIntStateOf(0)
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = emptyList(),
                    sourceLabels = listOf("4chan", "BBWChan"),
                    selectedSource = source,
                    onSelectSource = { source = it },
                    isLoading = source == 0,
                    errorMessage = if (source == 1) "The source is temporarily unavailable" else null,
                    onRetry = {},
                )
            }
        }
        selectSource("BBWChan")
        compose.onNodeWithText("The source is temporarily unavailable").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose source").assertIsDisplayed()
        selectSource("4chan")
        compose.onNodeWithTag(ReaderFeedTags.GRID).assertIsDisplayed()
        compose.onNodeWithText("The source is temporarily unavailable").assertDoesNotExist()
        assertNoFeedSelectors()
    }

    @Test
    fun reloadAndSearchStaySeparateWithinTheTwoActionToolbarBudget() {
        var retries = 0
        var searches = 0
        var boards = 0
        val rows = rows("fourchan")
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = rows,
                    onRetry = { retries++ },
                    onOpenSearch = { searches++ },
                    onOpenBoards = { boards++ },
                    onModeChange = {},
                    onOpenDownloads = {},
                )
            }
        }
        compose.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(18)
        val insideBar = hasAnyAncestor(hasTestTag(ReaderAppBarTags.BAR))
        compose.onNode(hasContentDescription("Reload") and insideBar).performClick()
        compose.onNodeWithTag(ReaderFeedTags.row(rows[18].id)).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(0, searches)
            assertEquals(0, boards)
        }
        compose.onNode(hasContentDescription("Search") and insideBar).performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(1, searches)
            assertEquals(0, boards)
        }
        // Menu and overflow frame exactly two primary actions; Downloads remains in overflow.
        compose.onAllNodes(insideBar and hasClickAction()).assertCountEquals(4)
    }

    @Test
    fun emptyHomeRetryAndBoardsOpenDifferentDestinations() {
        var canRetry by mutableStateOf(true)
        var retries = 0
        var boards = 0
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = emptyList(),
                    onRetry = if (canRetry) ({ retries++ }) else null,
                    onOpenBoards = { boards++ },
                )
            }
        }
        compose.onNodeWithText("Reload").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(0, boards)
        }
        compose
            .onNode(
                hasText("Boards") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
            ).performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(1, boards)
            canRetry = false
        }
        compose.onNodeWithText("Reload").assertDoesNotExist()
        compose
            .onNode(
                hasText("Boards") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
            ).assertIsDisplayed()
    }

    @Test
    fun refreshFailureKeepsTheReadingAnchorAndRetriesWithoutNavigation() {
        var failure by mutableStateOf<String?>(null)
        var retries = 0
        var boards = 0
        val rows = rows("fourchan")
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = rows,
                    errorMessage = failure,
                    onRetry = { retries++ },
                    onOpenBoards = { boards++ },
                )
            }
        }
        compose.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(18)
        compose.runOnIdle { failure = "Connection interrupted" }
        compose.onNodeWithTag(ReaderStateTags.REFRESH_FAILURE).assertIsDisplayed()
        compose.onNodeWithText("Connection interrupted").assertIsDisplayed()
        compose.onNodeWithTag(ReaderFeedTags.row(rows[18].id)).assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithTag(ReaderFeedTags.row(rows[18].id)).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(0, boards)
        }
    }

    @Test
    fun legacyFeedModeArgumentsCannotRestoreSelectorsOrResetTheReader() {
        var legacyFollowing by mutableStateOf(false)
        var modeChanges = 0
        val subscribedRows = rows("subscribed-source")
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = subscribedRows,
                    following = legacyFollowing,
                    onFollowingChange = { modeChanges++ },
                )
            }
        }
        compose.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(18)
        val retained = ReaderFeedTags.row(subscribedRows[18].id)
        val before = compose.onNodeWithTag(retained).getUnclippedBoundsInRoot()
        listOf(false, true, false).forEach { obsoleteValue ->
            compose.runOnIdle { legacyFollowing = obsoleteValue }
            assertNoFeedSelectors()
            val after = compose.onNodeWithTag(retained).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertEquals(
                "Obsolete feed mode arguments must not change the subscribed reader scope",
                before.top.value,
                after.top.value,
                0.5f,
            )
        }
        compose.runOnIdle { assertEquals(0, modeChanges) }
    }

    @Test
    fun readerHomeGridShowsOneFullWidthCardWithAnImageHalfTheViewport() {
        val rows = rows("fourchan")
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = rows,
                    mode = CatalogMode.GRID,
                    thumbnail = { _, modifier -> Box(modifier) },
                    onOpenBoards = {},
                    onOpenSearch = {},
                    onSettings = {},
                )
            }
        }

        val grid = compose.onNodeWithTag(ReaderFeedTags.GRID).getUnclippedBoundsInRoot()
        val expected = catalogGridSpec(grid.right - grid.left, grid.bottom - grid.top)
        val card = compose.onNodeWithTag(ReaderFeedTags.row(rows[0].id)).getUnclippedBoundsInRoot()
        val next = compose.onNodeWithTag(ReaderFeedTags.row(rows[1].id)).getUnclippedBoundsInRoot()
        val image =
            compose
                .onNodeWithTag(ReaderFeedTags.thumbnail(rows[0].id), useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        assertEquals("a portrait phone is one column", 1, expected.columns)
        assertEquals(expected.imageHeight.value, (image.bottom - image.top).value, 0.5f)
        assertEquals((grid.bottom - grid.top).value / 2, expected.imageHeight.value, 0.5f)
        assertEquals("the image spans the card", (card.right - card.left).value, (image.right - image.left).value, 0.5f)
        assertTrue("the second card starts below the first", next.top >= card.bottom)
    }

    private fun assertNoFeedSelectors() {
        listOf(
            ReaderFeedTags.FOLLOWING,
            ReaderFeedTags.FOR_YOU,
            TikTokFeedTags.FOLLOWING,
            TikTokFeedTags.FOR_YOU,
        ).forEach { tag ->
            compose.onNodeWithTag(tag).assertDoesNotExist()
        }
        compose.onNodeWithText("Following").assertDoesNotExist()
        compose.onNodeWithText("For You").assertDoesNotExist()
    }

    private fun selectSource(label: String) {
        compose.onNodeWithContentDescription("Choose source").performClick()
        compose.onNode(hasText(label) and radioButton()).performClick()
    }

    private fun radioButton() = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun rows(provider: String) = List(36) { row(provider, it) }

    private fun row(
        provider: String,
        index: Int,
    ) = FeedRow(
        id = "$provider/g/$index",
        subject = "Thread $index on $provider",
        board = "/g/",
        site = provider,
        activity = "1m",
        replies = 12,
        media = 1,
        mediaLabel = "MP4",
        excerpt = "A bounded reader preview for thread $index",
    )
}
