package com.orbin.feature.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.orbin.core.designsystem.theme.OrbinPreviewTheme
import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThumbnailSize
import kotlinx.collections.immutable.toPersistentList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the all-media wall at each stage of the sweep.
 *
 * Thumbnails do not load here — there is no network under Robolectric — so these cover the layout
 * around them: the progress header, the tile grid and badges, and the states with no grid at all.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class AllMediaScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Mid-sweep: tiles already on the wall, progress bar still filling. */
    @Test
    fun scanning() =
        capture("all_media_scanning") {
            state(items = items(9), boardsScanned = 12, boardsTotal = 70, isScanning = true)
        }

    /** The sweep finished, but a third of the boards failed — the wall is quietly partial. */
    @Test
    fun completeWithFailedBoards() =
        capture("all_media_failed_boards") {
            state(items = items(9), boardsScanned = 70, boardsTotal = 70, failedBoards = 23)
        }

    @Test
    fun complete() =
        capture("all_media_complete") {
            state(items = items(9), boardsScanned = 70, boardsTotal = 70)
        }

    @Test
    fun completeDark() =
        capture("all_media_complete_dark", darkTheme = true) {
            state(items = items(9), boardsScanned = 70, boardsTotal = 70)
        }

    /** The deep pass, which reports threads rather than boards. */
    @Test
    fun deepScanning() =
        capture("all_media_deep_scanning") {
            state(
                items = items(9),
                boardsScanned = 70,
                boardsTotal = 70,
                isDeepScanning = true,
                threadsScanned = 148,
                threadsTotal = 4210,
            )
        }

    /** The very first moments, before any board has come back. */
    @Test
    fun initialLoad() = capture("all_media_initial") { state(isScanning = true, boardsTotal = 70) }

    /** Swept everything, found nothing. */
    @Test
    fun empty() = capture("all_media_empty") { state(boardsScanned = 70, boardsTotal = 70) }

    /** Every board filtered out, so there was nothing to sweep. */
    @Test
    fun noBoards() = capture("all_media_no_boards") { state() }

    /** One tile per row, the widest the wall gets. */
    @Test
    fun fillColumns() =
        capture("all_media_fill") {
            state(items = items(3), boardsScanned = 70, boardsTotal = 70, size = ThumbnailSize.FILL)
        }

    private fun capture(
        name: String,
        darkTheme: Boolean = false,
        content: () -> AllMediaCase,
    ) {
        val case = content()
        composeRule.setContent {
            OrbinPreviewTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.size(411.dp, 891.dp)) {
                    Box {
                        AllMediaContent(
                            uiState = case.uiState,
                            isRefreshing = false,
                            thumbnailSize = case.size,
                            deepMediaScan = case.uiState.isDeepScanning,
                            onBack = {},
                            onRefresh = {},
                            onToggleDeepScan = {},
                            onOpenMedia = { _, _, _, _ -> },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private data class AllMediaCase(
        val uiState: AllMediaUiState,
        val size: ThumbnailSize,
    )

    private fun state(
        items: List<AllMediaItem> = emptyList(),
        boardsScanned: Int = 0,
        boardsTotal: Int = 0,
        isScanning: Boolean = false,
        failedBoards: Int = 0,
        isDeepScanning: Boolean = false,
        threadsScanned: Int = 0,
        threadsTotal: Int = 0,
        size: ThumbnailSize = ThumbnailSize.MEDIUM,
    ) = AllMediaCase(
        AllMediaUiState(
            items = items.toPersistentList(),
            boardsScanned = boardsScanned,
            boardsTotal = boardsTotal,
            isScanning = isScanning,
            failedBoards = failedBoards,
            isDeepScanning = isDeepScanning,
            threadsScanned = threadsScanned,
            threadsTotal = threadsTotal,
        ),
        size,
    )

    /** A spread of boards, plus a video and a spoiler, whose overlays draw without a network. */
    private fun items(count: Int): List<AllMediaItem> =
        (1..count).map { index ->
            val board = BOARDS[index % BOARDS.size]
            AllMediaItem(
                attachment =
                    MediaAttachment(
                        id = "file$index",
                        originalFileName = "file$index.jpg",
                        extension = "jpg",
                        type = if (index % 4 == 0) MediaType.VIDEO else MediaType.IMAGE,
                        sourceUrl = "https://example.invalid/$index.jpg",
                        thumbnailUrl = "https://example.invalid/${index}s.jpg",
                        width = 1024,
                        height = 768,
                        isSpoiler = index % 5 == 0,
                    ),
                key = ThreadKey(ProviderId("fourchan"), BoardId(board), ThreadId(index.toLong())),
                boardTitle = board,
                threadTitle = "Thread $index",
            )
        }

    private companion object {
        // The last is deliberately long: board ids are short, but nothing enforces that.
        val BOARDS = listOf("g", "a", "wsg", "lit", "verylongboardid")
    }
}
