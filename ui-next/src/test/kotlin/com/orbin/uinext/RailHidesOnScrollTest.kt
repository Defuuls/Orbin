package com.orbin.uinext

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scrolling down puts the rail away — on every screen that has one.
 *
 * This was the feed's behaviour alone. The catalog is drawn from the same row in the same layouts
 * and kept its rail pinned, and the media wall did too, so turning the setting on produced an app
 * that reclaimed the screen on one list and not on the next. Asserted per screen rather than
 * assumed from the shared scaffold, because "they share a scaffold" is exactly the kind of claim
 * that stays true right up until one screen stops passing the flag through.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class RailHidesOnScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the catalog puts its rail away as the reader scrolls down`() {
        composeRule.setContent {
            NextTheme {
                BoardScreen(
                    board = "/g/",
                    description = "Technology",
                    itemCount = ROWS.size,
                    rowAt = { ROWS.getOrNull(it) },
                    hideRailOnScroll = true,
                )
            }
        }
        railDetail(CATALOG).assertExists()
        composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        railDetail(CATALOG).assertDoesNotExist()
    }

    @Test
    fun `the catalog keeps its rail when the setting is off`() {
        composeRule.setContent {
            NextTheme {
                BoardScreen(
                    board = "/g/",
                    description = "Technology",
                    itemCount = ROWS.size,
                    rowAt = { ROWS.getOrNull(it) },
                    hideRailOnScroll = false,
                )
            }
        }
        composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        railDetail(CATALOG).assertExists()
    }

    @Test
    fun `the media wall puts its rail away as the reader scrolls down`() {
        composeRule.setContent {
            NextTheme {
                MediaWallScreen(
                    scanned = 4,
                    total = 4,
                    failed = 0,
                    cells = (1..40).map { MediaCell(id = "$it", board = "/g/") },
                    hideRailOnScroll = true,
                )
            }
        }
        railDetail(SWEPT).assertExists()
        composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        railDetail(SWEPT).assertDoesNotExist()
    }

    /** The screen reports the change, so the shell can take the system bars with it. */
    @Test
    fun `a screen tells the shell when its rail goes away`() {
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
        assert(reported.contains(false)) { "expected the screen to report a hidden rail, got $reported" }
    }

    /**
     * The rail's detail line.
     *
     * Matched as a substring because the rail spaces the detail off the location with two literal
     * spaces in the string rather than with layout, so the node's text is "  catalog".
     */
    private fun railDetail(text: String) = composeRule.onNodeWithText(text, substring = true)

    private companion object {
        /** Only the rail says these; the titles above them do not. */
        const val CATALOG = "catalog"
        const val SWEPT = "4/4 swept"

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
