package com.orbin.uinext

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scrolling down puts the tab pill away, and secondary screens have no floating bar to put away.
 *
 * The catalog and media wall used to carry a floating name bar that repeated their own large title
 * over the content. They now draw no bottom chrome at all, the same as a thread, so the only bar
 * left to hide is the Feed / Media / Boards pill. The screens still report scroll direction to the shell
 * so the system bars can follow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class RailHidesOnScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the feed puts its tab pill away as the reader scrolls down`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, onOpenDownloads = {}, hideRailOnScroll = true) }
        }
        composeRule.onNodeWithText(DOWNLOADS_TAB).assertExists()
        composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(DOWNLOADS_TAB).assertDoesNotExist()
    }

    @Test
    fun `the feed keeps its tab pill when the setting is off`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, onOpenDownloads = {}, hideRailOnScroll = false) }
        }
        composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(DOWNLOADS_TAB).assertExists()
    }

    @Test
    fun `the media wall names itself once, with no floating bar repeating the title`() {
        composeRule.setContent {
            NextTheme {
                MediaWallScreen(
                    scanned = 4,
                    total = 4,
                    failed = 0,
                    cells = (1..40).map { MediaCell(id = "$it", board = "/g/") },
                )
            }
        }
        composeRule.onAllNodesWithText("All media").assertCountEquals(1)
    }

    /** The screen reports the change, so the shell can take the system bars with it. */
    @Test
    fun `a screen tells the shell when its chrome goes away`() {
        val reported = mutableListOf<Boolean>()
        composeRule.setContent {
            NextTheme {
                BoardScreen(
                    board = "/g/",
                    description = "Technology",
                    itemCount = ROWS.size,
                    rowAt = { ROWS.getOrNull(it) },
                    hideRailOnScroll = true,
                    onChromeVisibleChange = { reported += it },
                )
            }
        }
        composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assert(reported.contains(false)) { "expected the screen to report hidden chrome, got $reported" }
    }

    private companion object {
        const val DOWNLOADS_TAB = "Downloads"

        val ROWS =
            (1..30).map { index ->
                FeedRow(
                    subject = "Thread number $index",
                    board = "/g/",
                    activity = "${index}m",
                    replies = index,
                    media = index,
                )
            }
    }
}
