package com.orbin.minimal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.uinext.NextTheme
import kotlinx.collections.immutable.toPersistentList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class MinimalScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun boardsPopulated() = capture("minimal_boards_populated") { Boards(sampleBoards()) }

    @Test
    fun boardsPopulatedDark() = capture("minimal_boards_populated_dark", dark = true) { Boards(sampleBoards()) }

    @Test
    fun boardsLoading() = capture("minimal_boards_loading") { Boards(emptyList(), isLoading = true) }

    @Test
    fun boardsEmpty() = capture("minimal_boards_empty") { Boards(emptyList()) }

    @Test
    fun boardsError() = capture("minimal_boards_error") { Boards(emptyList(), error = FETCH_ERROR) }

    @Test
    fun boardsCachedError() =
        capture("minimal_boards_cached_error") {
            Boards(sampleBoards(), error = FETCH_ERROR)
        }

    @Test
    fun boardsMaxText() =
        capture("minimal_boards_max_text", fontScale = 2f) {
            Boards(sampleBoards())
        }

    @Test
    fun boardsNarrow() =
        capture("minimal_boards_narrow", width = 320.dp, height = 700.dp) {
            Boards(sampleBoards())
        }

    @Test
    fun launcherIcon() {
        composeRule.setContent {
            Box(
                modifier =
                    Modifier
                        .size(ICON_SIZE)
                        .background(colorResource(R.color.ic_launcher_background)),
            ) {
                Image(
                    painter = painterResource(com.orbin.core.designsystem.R.drawable.ic_launcher_orbit_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/minimal_launcher_icon.png")
    }

    @Composable
    private fun Boards(
        boards: List<SubscribableBoard>,
        isLoading: Boolean = false,
        error: String? = null,
    ) = MinimalBoardsContent(
        state =
            MinimalBoardsUiState(
                boards = boards.toPersistentList(),
                isRefreshing = isLoading,
                refreshError = error,
            ),
        onBack = {},
        onRefresh = {},
        onToggle = { _, _ -> },
    )

    private fun capture(
        name: String,
        dark: Boolean = false,
        fontScale: Float = 1f,
        width: Dp = 411.dp,
        height: Dp = 891.dp,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
            ) {
                NextTheme(darkTheme = dark) {
                    Surface(modifier = Modifier.size(width, height)) { Box { content() } }
                }
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
        val ICON_SIZE = 108.dp
        val TECH = Board(BoardId("g"), "Technology")
        val ANIME = Board(BoardId("a"), "Anime & Manga")
        const val LONG_BOARD_TITLE = "Worksafe Gifs, with a title long enough to need truncating"
        const val FETCH_ERROR = "Could not reach the server"
    }
}
