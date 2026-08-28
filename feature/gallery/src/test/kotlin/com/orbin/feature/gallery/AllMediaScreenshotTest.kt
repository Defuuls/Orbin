package com.orbin.feature.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.test.FakeImageLoaderEngine
import com.github.takahirom.roborazzi.captureRoboImage
import com.orbin.core.designsystem.theme.OrbinPreviewTheme
import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import kotlinx.collections.immutable.toPersistentList
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the all-media wall at each stage of the sweep.
 *
 * Thumbnails are served by a fake image loader rather than left to fail. Letting them hit the
 * network made the captures depend on *how* the request failed: behind a proxy it stayed pending
 * and the tiles rendered blank, while on a CI runner DNS failed immediately and the tiles rendered
 * their error state. The goldens recorded one and the runner produced the other, so the same code
 * passed in one place and failed in the other.
 *
 * Replacing the singleton loader is what `DelicateCoilApi` warns about: it is process-wide and
 * racy if anything else is loading. Nothing else is here, and `@After` puts it back.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class AllMediaScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun installFakeImageLoader() = loadThumbnailsAs(TILE_COLOUR)

    @After
    fun resetImageLoader() = SingletonImageLoader.reset()

    /** Every thumbnail request resolves to a flat colour, synchronously and identically. */
    private fun loadThumbnailsAs(colour: Int) {
        val engine = FakeImageLoaderEngine.Builder().default(ColorImage(colour)).build()
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context).components { add(engine) }.build()
        }
    }

    /** Every thumbnail request fails, so the tiles render their "Image unavailable" state. */
    private fun failAllThumbnails() {
        val engine =
            FakeImageLoaderEngine
                .Builder()
                .intercept({ true }, { error("thumbnail unavailable") })
                .build()
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context).components { add(engine) }.build()
        }
    }

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

    /**
     * Thumbnails that failed to load.
     *
     * Worth a golden of its own because it is common rather than exceptional here: the sweep asks
     * for thumbnails from every board at once, and the loader has a dedicated message for HTTP 429.
     * It also records a defect — the tile's "Image unavailable" text is bottom-centred while the
     * board badge is bottom-start, so on a wall-sized tile the two overlap illegibly.
     */
    @Test
    fun failedThumbnails() {
        failAllThumbnails()
        capture("all_media_failed_thumbnails") {
            state(items = items(9), boardsScanned = 70, boardsTotal = 70)
        }
    }

    /**
     * The thread media browser's tiles: a plain image, a video with its play badge, and a spoiler.
     *
     * The spoiler is the reason this golden exists. This grid drew no spoiler overlay at all, so a
     * file the poster had marked was rendered in the clear — the one case where "it looks fine" is
     * indistinguishable from the bug.
     */
    @Test
    fun galleryBrowserTiles() {
        composeRule.setContent {
            OrbinPreviewTheme {
                Surface(modifier = Modifier.size(411.dp, 160.dp)) {
                    Row(modifier = Modifier.padding(8.dp)) {
                        listOf(
                            tile(MediaType.IMAGE, isSpoiler = false),
                            tile(MediaType.VIDEO, isSpoiler = false),
                            tile(MediaType.IMAGE, isSpoiler = true),
                        ).forEach { attachment ->
                            Box(modifier = Modifier.width(120.dp).padding(4.dp)) {
                                MediaTile(attachment = attachment, onClick = {})
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/gallery_browser_tiles.png")
    }

    private fun tile(
        type: MediaType,
        isSpoiler: Boolean,
    ) = MediaAttachment(
        id = "tile-$type-$isSpoiler",
        originalFileName = "file.jpg",
        extension = "jpg",
        type = type,
        sourceUrl = "https://example.invalid/file.jpg",
        thumbnailUrl = "https://example.invalid/files.jpg",
        width = 1024,
        height = 768,
        isSpoiler = isSpoiler,
    )

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
                        NextAllMediaContent(
                            uiState = case.uiState,
                            isRefreshing = false,
                            onRefresh = {},
                            onOpenMedia = { _, _, _, _ -> },
                            onOpenCommands = {},
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

        // Opaque mid-grey: distinguishable from both themes' backgrounds, so a tile that failed
        // to draw at all is not mistaken for one that drew correctly.
        const val TILE_COLOUR = 0xFF6E7A8A.toInt()
    }
}
