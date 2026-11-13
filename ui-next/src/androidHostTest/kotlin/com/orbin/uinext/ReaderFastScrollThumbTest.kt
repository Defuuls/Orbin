package com.orbin.uinext

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import com.orbin.uinext.tokens.ReaderSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real thread scrolling, header/footer endpoints, and interaction coexistence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ReaderFastScrollThumbTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var state: LazyListState

    @Test
    fun `a short thread has no fast scroll target`() {
        showThread(count = 3)

        composeRule.onNodeWithTag(ReaderFastScrollTags.THUMB).assertDoesNotExist()
    }

    @Test
    fun `the files layout does not expose the posts scrollbar`() {
        showThread(layout = ThreadLayout.FILES)

        composeRule.onNodeWithTag(ReaderFastScrollTags.THUMB).assertDoesNotExist()
    }

    @Test
    fun `accessible progress reaches the true header middle post and footer`() {
        showThread()
        composeRule
            .onNodeWithTag(ReaderFastScrollTags.THUMB)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(ReaderSize.touchTarget)
            .assertHeightIsEqualTo(ReaderSize.touchTarget)

        moveTo(0.5f)
        composeRule.runOnIdle {
            assertEquals(POST_COUNT + 2, state.layoutInfo.totalItemsCount)
            assertEquals(61, state.firstVisibleItemIndex)
        }
        composeRule.onNodeWithText("Post body 60", useUnmergedTree = true).assertIsDisplayed()

        moveTo(1f)
        composeRule.runOnIdle { assertFalse(state.canScrollForward) }
        composeRule.onNodeWithTag(ThreadTags.STATUS).assertIsDisplayed()

        moveTo(0f)
        composeRule.onNodeWithText("Thread summary").assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(state.canScrollBackward) }
    }

    @Test
    fun `dragging the thumb jumps forward through the long thread`() {
        showThread()
        moveTo(0.25f)
        val before = composeRule.runOnIdle { state.firstVisibleItemIndex }

        composeRule.onNodeWithTag(ReaderFastScrollTags.THUMB).performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y + height * 3f), durationMillis = 300)
        }

        composeRule.runOnIdle { assertTrue(state.firstVisibleItemIndex > before + 1) }
    }

    @Test
    fun `thumb overlay leaves post taps and normal list swipes intact`() {
        val opened = mutableListOf<String>()
        showThread(onPost = { opened += it.id })

        val thumb = composeRule.onNodeWithTag(ReaderFastScrollTags.THUMB).getUnclippedBoundsInRoot()
        val post =
            composeRule.onAllNodesWithTag(ThreadTags.POST, useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val left = maxOf(thumb.left.value, post.left.value)
        val right = minOf(thumb.right.value, post.right.value)
        val top = maxOf(thumb.top.value, post.top.value)
        val bottom = minOf(thumb.bottom.value, post.bottom.value)
        assertTrue("The thumb must overlap the post interior: $thumb in $post", right > left && bottom > top)
        println("Fast-scroll tap target: thumb=$thumb; firstPost=$post")
        // Its center can fall on the summary/header boundary; tap inside both measured targets.
        val xFraction = ((left + right) / 2 - thumb.left.value) / (thumb.right - thumb.left).value
        val yFraction = ((top + bottom) / 2 - thumb.top.value) / (thumb.bottom - thumb.top).value
        composeRule.onNodeWithTag(ReaderFastScrollTags.THUMB).performTouchInput {
            click(Offset(width * xFraction, height * yFraction))
        }
        composeRule.runOnIdle { assertEquals("Tap through $thumb to $post", listOf("post-0"), opened) }
        composeRule.onNodeWithText("Post body 0", useUnmergedTree = true).performClick()
        composeRule.runOnIdle { assertEquals(listOf("post-0", "post-0"), opened) }

        composeRule.onNodeWithTag(ThreadTags.LIST).performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertTrue(state.firstVisibleItemIndex > 0)
            assertEquals(2, opened.size)
        }
    }

    private fun moveTo(fraction: Float) {
        composeRule.onNodeWithTag(ReaderFastScrollTags.THUMB).performSemanticsAction(SemanticsActions.SetProgress) {
            assertTrue(it(fraction))
        }
        composeRule.waitForIdle()
    }

    private fun showThread(
        count: Int = POST_COUNT,
        layout: ThreadLayout = ThreadLayout.POSTS,
        onPost: ((Post) -> Unit)? = null,
    ) {
        val posts =
            List(count) { index ->
                Post(number = "$index", time = "1m", body = "Post body $index", id = "post-$index")
            }
        composeRule.setContent {
            val list = rememberLazyListState()
            SideEffect { state = list }
            NextTheme {
                ThreadScreen(
                    subject = "A long thread",
                    board = "/g/",
                    subtitle = "Thread summary",
                    posts = posts,
                    layout = layout,
                    listState = list,
                    onPostClick = onPost,
                )
            }
        }
    }

    private companion object {
        const val POST_COUNT = 120
    }
}
