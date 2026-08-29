package com.orbin.minimal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.uinext.NextTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the one screen Orbin Minimal still draws for itself, in every state a reader can land on.
 *
 * The feed and the reader are the full client's own screens now, and are captured in `:ui-next`
 * and `:feature:gallery` where they live — capturing them a second time here would only assert
 * that this app calls them, which its navigation graph already says. The board picker is the join
 * this app owns: which of the shared screen's states each of its own states maps onto.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class MinimalScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun boardsPopulated() = capture("minimal_boards_populated") { Boards(sampleBoards()) }

    /** The same list in the dark palette, which the picker follows the system into. */
    @Test
    fun boardsPopulatedDark() = capture("minimal_boards_populated_dark", dark = true) { Boards(sampleBoards()) }

    /** Fetch in flight and nothing cached yet — the only case that offers no retry. */
    @Test
    fun boardsLoading() = capture("minimal_boards_loading") { Boards(emptyList(), isLoading = true) }

    /** The provider answered with no boards. Previously indistinguishable from still loading. */
    @Test
    fun boardsEmpty() = capture("minimal_boards_empty") { Boards(emptyList()) }

    /** The fetch failed. Previously a spinner with no way out; now it offers a retry. */
    @Test
    fun boardsError() = capture("minimal_boards_error") { Boards(emptyList(), error = FETCH_ERROR) }

    @Composable
    private fun Boards(
        boards: List<SubscribableBoard>,
        isLoading: Boolean = false,
        error: String? = null,
    ) = MinimalBoardsContent(
        boards = boards,
        isLoading = isLoading,
        errorMessage = error,
        onBack = {},
        onRefresh = {},
        onToggle = { _, _ -> },
    )

    private fun capture(
        name: String,
        dark: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            // The screen brings its own theme; this only forces the palette the capture is of, and
            // gives the content a fixed phone-sized frame to fill rather than an unbounded root.
            NextTheme(darkTheme = dark) {
                Surface(modifier = Modifier.size(411.dp, 891.dp)) { Box { content() } }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun sampleBoards() =
        listOf(
            SubscribableBoard(ANIME, isSubscribed = true),
            SubscribableBoard(TECH, isSubscribed = true),
            SubscribableBoard(Board(BoardId("lit"), "Literature"), isSubscribed = false),
            SubscribableBoard(Board(BoardId("wsg"), LONG_BOARD_TITLE), isSubscribed = false),
        )

    private companion object {
        val TECH = Board(BoardId("g"), "Technology")
        val ANIME = Board(BoardId("a"), "Anime & Manga")
        const val LONG_BOARD_TITLE = "Worksafe Gifs, with a title long enough to need truncating"
        const val FETCH_ERROR = "Could not reach the server"
    }
}
