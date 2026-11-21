package com.orbin.uinext.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.orbin.uinext.screens.BoardScreen
import com.orbin.uinext.screens.BoardScreenActions
import com.orbin.uinext.screens.BoardScreenState
import com.orbin.uinext.screens.CatalogMode
import com.orbin.uinext.screens.CatalogTags
import com.orbin.uinext.screens.FeedScreen
import com.orbin.uinext.screens.FeedScreenActions
import com.orbin.uinext.screens.FeedScreenState
import com.orbin.uinext.screens.MediaCell
import com.orbin.uinext.screens.Post
import com.orbin.uinext.screens.ReaderFeedTags
import com.orbin.uinext.screens.ThreadLayout
import com.orbin.uinext.screens.ThreadScreen
import com.orbin.uinext.screens.ThreadTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The visibility rules on their own, without a screen. */
class ScrollJumpVisibilityTest {
    @Test
    fun `hidden while idle however far down the reader is`() {
        assertEquals(ScrollJumpVisibility.HIDDEN, visibility(first = 80, visible = 10, forward = true, active = false))
    }

    @Test
    fun `hidden within the first screen of items`() {
        assertEquals(ScrollJumpVisibility.HIDDEN, visibility(first = 0, visible = 10, forward = true, active = true))
        assertEquals(ScrollJumpVisibility.HIDDEN, visibility(first = 9, visible = 10, forward = true, active = true))
    }

    @Test
    fun `both buttons a screen or more from the top with content below`() {
        assertEquals(
            ScrollJumpVisibility(top = true, bottom = true),
            visibility(first = 10, visible = 10, forward = true, active = true),
        )
    }

    @Test
    fun `bottom hides at the end while top stays`() {
        assertEquals(
            ScrollJumpVisibility(top = true, bottom = false),
            visibility(first = 90, visible = 10, forward = false, active = true),
        )
    }

    @Test
    fun `an empty or unmeasured list never shows the controls`() {
        assertEquals(ScrollJumpVisibility.HIDDEN, visibility(first = 0, visible = 0, forward = false, active = true))
        assertEquals(ScrollJumpVisibility.HIDDEN, visibility(first = 5, visible = 0, forward = true, active = true))
    }

    @Test
    fun `any reports whether either button shows`() {
        assertFalse(ScrollJumpVisibility.HIDDEN.any)
        assertTrue(ScrollJumpVisibility(top = true, bottom = false).any)
        assertTrue(ScrollJumpVisibility(top = false, bottom = true).any)
    }

    private fun visibility(
        first: Int,
        visible: Int,
        forward: Boolean,
        active: Boolean,
    ) = scrollJumpVisibility(first, visible, forward, active)
}

/** Real screens: the controls' timing, their jumps, and where they sit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ReaderScrollJumpButtonsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var listState: LazyListState

    @Test
    fun `nothing shows before the reader scrolls`() {
        showThread()

        composeRule.onNodeWithTag(ReaderScrollJumpTags.CONTROLS).assertDoesNotExist()
    }

    @Test
    fun `a short thread never offers the controls`() {
        showThread(count = 3)
        composeRule.onNodeWithTag(ThreadTags.LIST).performScrollToIndex(4)

        composeRule.onNodeWithTag(ReaderScrollJumpTags.CONTROLS).assertDoesNotExist()
    }

    @Test
    fun `scrolling far down shows both buttons with accessible labels`() {
        showThread()
        composeRule.onNodeWithTag(ThreadTags.LIST).performScrollToIndex(60)

        // The app bar title also scrolls to the top, so match the button by its tag as well.
        composeRule
            .onNode(hasTestTag(ReaderScrollJumpTags.TOP) and hasContentDescription("Scroll to top"))
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule
            .onNode(hasTestTag(ReaderScrollJumpTags.BOTTOM) and hasContentDescription("Scroll to bottom"))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun `the controls fade once scrolling has been still for the linger time`() {
        showThread()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(ThreadTags.LIST).performScrollToIndex(60)
        composeRule.mainClock.advanceTimeBy(SCROLL_JUMP_LINGER_MILLIS / 2)
        composeRule.onNodeWithTag(ReaderScrollJumpTags.CONTROLS).assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(SCROLL_JUMP_LINGER_MILLIS + FADE_MILLIS)
        composeRule.onNodeWithTag(ReaderScrollJumpTags.CONTROLS).assertDoesNotExist()
    }

    @Test
    fun `top returns to the thread header and the controls leave`() {
        showThread()
        composeRule.onNodeWithTag(ThreadTags.LIST).performScrollToIndex(60)

        composeRule.onNodeWithTag(ReaderScrollJumpTags.TOP).performClick()

        composeRule.runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex)
            assertFalse(listState.canScrollBackward)
        }
        composeRule.onNodeWithText("Thread summary").assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderScrollJumpTags.CONTROLS).assertDoesNotExist()
    }

    @Test
    fun `bottom reaches the thread footer and hides itself`() {
        showThread()
        composeRule.onNodeWithTag(ThreadTags.LIST).performScrollToIndex(30)

        composeRule.onNodeWithTag(ReaderScrollJumpTags.BOTTOM).performClick()

        composeRule.runOnIdle { assertFalse(listState.canScrollForward) }
        composeRule.onNodeWithTag(ThreadTags.STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderScrollJumpTags.BOTTOM).assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderScrollJumpTags.TOP).assertIsDisplayed()
    }

    @Test
    fun `bottom reaches the true end when the last item is taller than the screen`() {
        composeRule.setContent {
            val list = rememberLazyListState()
            SideEffect { listState = list }
            NextTheme {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize().testTag(PLAIN_LIST), state = list) {
                        items(60) { Box(Modifier.fillMaxWidth().height(80.dp)) }
                        item { Box(Modifier.fillMaxWidth().height(3_000.dp)) }
                    }
                    ReaderScrollJumpButtons(rememberScrollJumpTarget(list))
                }
            }
        }
        composeRule.onNodeWithTag(PLAIN_LIST).performScrollToIndex(20)

        composeRule.onNodeWithTag(ReaderScrollJumpTags.BOTTOM).performClick()

        composeRule.runOnIdle {
            assertEquals(60, listState.firstVisibleItemIndex)
            assertFalse("Aligning the tall item's top is not the end", listState.canScrollForward)
        }
    }

    @Test
    fun `the files layout jumps its own grid of files`() {
        showThread(layout = ThreadLayout.FILES)
        composeRule.onNodeWithTag(ThreadTags.LIST).performScrollToIndex(40)
        composeRule.onNodeWithTag(ReaderScrollJumpTags.BOTTOM).performClick()
        composeRule.onNodeWithTag(ReaderScrollJumpTags.BOTTOM).assertDoesNotExist()

        composeRule.onNodeWithTag(ReaderScrollJumpTags.TOP).performClick()

        // The header row holds the media filter, which only the top of the files grid shows.
        composeRule.onNodeWithText("All", substring = true, useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("The posts list is untouched", 0, listState.firstVisibleItemIndex) }
    }

    @Test
    fun `the thread controls stay clear of the fast scroll thumb`() {
        showThread()
        composeRule.onNodeWithTag(ThreadTags.LIST).performScrollToIndex(60)

        val thumb = composeRule.onNodeWithTag(ReaderFastScrollTags.THUMB).getUnclippedBoundsInRoot()
        val controls = composeRule.onNodeWithTag(ReaderScrollJumpTags.CONTROLS).getUnclippedBoundsInRoot()

        assertFalse("Controls $controls overlap the thumb $thumb", controls.overlaps(thumb))
    }

    @Test
    fun `catalog bottom reaches the last thread and top comes back`() {
        val grid = LazyGridState()
        composeRule.setContent {
            NextTheme {
                BoardScreen(
                    state =
                        BoardScreenState(
                            board = "/g/",
                            description = "Technology",
                            itemCount = ROWS.size,
                            rowAt = { ROWS.getOrNull(it) },
                            mode = CatalogMode.GRID,
                        ),
                    actions = BoardScreenActions(),
                    gridState = grid,
                    thumbnail = { _, modifier -> Box(modifier) },
                )
            }
        }
        composeRule.onNodeWithTag(CatalogTags.GRID).performScrollToIndex(40)

        composeRule.onNodeWithTag(ReaderScrollJumpTags.BOTTOM).performClick()
        composeRule.runOnIdle { assertFalse(grid.canScrollForward) }

        composeRule.onNodeWithTag(ReaderScrollJumpTags.TOP).performClick()
        composeRule.runOnIdle {
            assertEquals(0, grid.firstVisibleItemIndex)
            assertFalse(grid.canScrollBackward)
        }
    }

    @Test
    fun `feed bottom reaches the loaded end and asks for more`() {
        var loadMore = 0
        showFeed(onLoadMore = { loadMore++ })
        composeRule.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(20)
        val before = composeRule.runOnIdle { loadMore }

        composeRule.onNodeWithTag(ReaderScrollJumpTags.BOTTOM).performClick()

        composeRule.onNodeWithText(ROWS.last().subject, useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnIdle { assertTrue("The feed pages on from its end", loadMore > before) }
    }

    @Test
    fun `feed top returns to the first thread`() {
        showFeed()
        composeRule.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(50)

        composeRule.onNodeWithTag(ReaderScrollJumpTags.TOP).performClick()

        composeRule.onNodeWithText(ROWS.first().subject, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderScrollJumpTags.CONTROLS).assertDoesNotExist()
    }

    private fun showFeed(onLoadMore: (() -> Unit)? = null) {
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    state = FeedScreenState(rows = ROWS, mode = CatalogMode.LIST),
                    actions = FeedScreenActions(onLoadMore = onLoadMore),
                    thumbnail = { _, modifier -> Box(modifier) },
                )
            }
        }
    }

    private fun showThread(
        count: Int = POST_COUNT,
        layout: ThreadLayout = ThreadLayout.POSTS,
    ) {
        val posts =
            List(count) { index ->
                Post(
                    number = "$index",
                    time = "1m",
                    body = "Post body $index",
                    id = "post-$index",
                )
            }
        composeRule.setContent {
            val list = rememberLazyListState()
            SideEffect { listState = list }
            NextTheme {
                ThreadScreen(
                    subject = "A long thread",
                    board = "/g/",
                    subtitle = "Thread summary",
                    posts = posts,
                    layout = layout,
                    listState = list,
                    files = FILES,
                )
            }
        }
    }

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private companion object {
        const val POST_COUNT = 120
        const val PLAIN_LIST = "plain-list"
        const val FADE_MILLIS = 1_000L
        val FILES = List(300) { MediaCell(id = "file-$it", board = "/g/", kind = MediaKind.IMAGE) }
        val ROWS =
            List(60) { index ->
                FeedRow(
                    id = "fourchan/g/$index",
                    subject = "Feed thread $index",
                    board = "/g/",
                    activity = "1m",
                    replies = 3,
                    media = 1,
                )
            }
    }
}
