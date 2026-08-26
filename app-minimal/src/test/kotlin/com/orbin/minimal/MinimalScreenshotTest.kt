package com.orbin.minimal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.feature.home.SubscribedBoardFeed
import com.orbin.feature.home.SubscribedFeedUiState
import kotlinx.collections.immutable.persistentListOf
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
 * Renders Orbin Minimal's two screens at phone size, in every state a reader can land on.
 *
 * This app has never run on a device, and its screens are pure presentation over the shared
 * layers, so composing them against fixed state is very nearly the whole of what a manual pass
 * would check: that each state draws something legible rather than a blank or a clipped row.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class MinimalScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Thumbnails resolve to a flat colour rather than being left to fail. Letting them reach the
     * network makes a capture depend on *how* the request fails, which differs by environment —
     * the same trap the media wall's goldens fell into.
     */
    @Before
    fun installFakeImageLoader() {
        val engine = FakeImageLoaderEngine.Builder().default(ColorImage(TILE_COLOUR)).build()
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context).components { add(engine) }.build()
        }
    }

    @After
    fun resetImageLoader() = SingletonImageLoader.reset()

    @Test
    fun feedPopulated() = capture("minimal_feed_populated") { MinimalFeed(populatedState()) }

    @Test
    fun feedPopulatedDark() = capture("minimal_feed_populated_dark", true) { MinimalFeed(populatedState()) }

    @Test
    fun feedLoading() = capture("minimal_feed_loading") { MinimalFeed(SubscribedFeedUiState.Loading) }

    @Test
    fun feedError() =
        capture("minimal_feed_error") {
            MinimalFeed(SubscribedFeedUiState.Error("Could not reach the server"))
        }

    /** Subscribed to nothing at all — the state a fresh install opens in. */
    @Test
    fun feedNoSubscriptions() =
        capture("minimal_feed_no_subscriptions") {
            MinimalFeed(SubscribedFeedUiState.Success(persistentListOf()))
        }

    /** Subscribed, but every board came back empty. */
    @Test
    fun feedNoThreads() =
        capture("minimal_feed_no_threads") {
            MinimalFeed(
                SubscribedFeedUiState.Success(
                    persistentListOf(SubscribedBoardFeed(TECH, persistentListOf(), null)),
                ),
            )
        }

    /**
     * A thread with exactly one reply, and one with none. The summary is a plain format string
     * rather than a plural, so this is where that shows.
     */
    @Test
    fun feedReplyCounts() =
        capture("minimal_feed_reply_counts") {
            MinimalFeed(
                SubscribedFeedUiState.Success(
                    persistentListOf(
                        SubscribedBoardFeed(
                            TECH,
                            persistentListOf(
                                thread(TECH, 1L, subject = "Exactly one reply", replies = 1),
                                thread(TECH, 3L, subject = "No replies at all", replies = 0),
                                thread(TECH, 2L, subject = null, comment = "", replies = 5),
                            ),
                            null,
                        ),
                    ),
                ),
            )
        }

    /** A long unbroken title and a long board id, to see where a row clips. */
    @Test
    fun feedLongTitles() =
        capture("minimal_feed_long_titles") {
            MinimalFeed(
                SubscribedFeedUiState.Success(
                    persistentListOf(
                        SubscribedBoardFeed(
                            TECH,
                            persistentListOf(
                                thread(TECH, 1L, subject = LONG_SUBJECT, replies = 12345),
                                thread(TECH, 2L, subject = UNBROKEN, replies = 0),
                            ),
                            null,
                        ),
                    ),
                ),
            )
        }

    /**
     * Enough threads for the fast scrollbar to appear. The wall reserves grid padding for its
     * scrollbar; this list does not, so this is where any overlap with the rows would show.
     */
    @Test
    fun feedWithScrollbar() =
        capture("minimal_feed_scrollbar") {
            MinimalFeed(
                SubscribedFeedUiState.Success(
                    persistentListOf(
                        SubscribedBoardFeed(
                            TECH,
                            (1..40)
                                .map { thread(TECH, it.toLong(), subject = SCROLLBAR_ROW, replies = it) }
                                .toPersistentList(),
                            null,
                        ),
                    ),
                ),
            )
        }

    /**
     * Rows carrying media. An image, a video (which autoplays while on screen, so this captures the
     * player rather than the thumbnail), a spoilered video that must NOT autoplay, and a row with
     * no attachment at all so the text-only shape still gets covered.
     */
    @Test
    fun feedWithMedia() =
        capture("minimal_feed_media") {
            MinimalFeed(
                SubscribedFeedUiState.Success(
                    persistentListOf(
                        SubscribedBoardFeed(
                            TECH,
                            persistentListOf(
                                thread(TECH, 4L, subject = "An image", media = MediaType.IMAGE),
                                thread(TECH, 3L, subject = "A video", media = MediaType.VIDEO),
                                thread(
                                    TECH,
                                    2L,
                                    subject = "A spoilered video",
                                    media = MediaType.VIDEO,
                                    spoiler = true,
                                ),
                                thread(TECH, 1L, subject = "No attachment"),
                            ),
                            null,
                        ),
                    ),
                ),
            )
        }

    @Test
    fun boardsPopulated() = capture("minimal_boards_populated") { Boards(sampleBoards()) }

    /** Fetch in flight and nothing cached yet — the only case that should show a spinner. */
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

    @Composable
    private fun MinimalFeed(state: SubscribedFeedUiState) {
        MinimalFeedContent(
            uiState = state,
            isRefreshing = false,
            providerId = PROVIDER,
            visitedKeys = setOf(ThreadKey(ProviderId(PROVIDER), TECH.id, ThreadId(2L))),
            onRefresh = {},
            onOpenThread = { _, _, _, _ -> },
            onOpenBoards = {},
        )
    }

    private fun capture(
        name: String,
        darkTheme: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            OrbinPreviewTheme(darkTheme = darkTheme) {
                // A fixed phone-sized frame: the screens fill their parent, and an unconstrained
                // root would render them at whatever the content happened to need.
                Surface(modifier = Modifier.size(411.dp, 891.dp)) { Box { content() } }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun populatedState(): SubscribedFeedUiState =
        SubscribedFeedUiState.Success(
            persistentListOf(
                SubscribedBoardFeed(
                    TECH,
                    listOf(
                        thread(TECH, 1L, subject = "What are you working on?", replies = 312, bumpedAt = 900),
                        thread(TECH, 2L, subject = "Daily programming thread", replies = 47, bumpedAt = 700),
                    ).toPersistentList(),
                    null,
                ),
                SubscribedBoardFeed(
                    ANIME,
                    listOf(
                        thread(ANIME, 3L, subject = null, comment = COMMENT_ONLY, replies = 8, bumpedAt = 800),
                        thread(ANIME, 4L, subject = "Season discussion", replies = 1204, bumpedAt = 600),
                    ).toPersistentList(),
                    null,
                ),
            ),
        )

    private fun sampleBoards() =
        listOf(
            SubscribableBoard(ANIME, isSubscribed = true),
            SubscribableBoard(TECH, isSubscribed = true),
            SubscribableBoard(Board(BoardId("lit"), "Literature"), isSubscribed = false),
            SubscribableBoard(Board(BoardId("wsg"), LONG_BOARD_TITLE), isSubscribed = false),
        )

    private fun thread(
        board: Board,
        id: Long,
        subject: String? = "Subject $id",
        comment: String = "",
        replies: Int = 3,
        bumpedAt: Long = id * 100,
        media: MediaType? = null,
        spoiler: Boolean = false,
    ) = CatalogThread(
        key = ThreadKey(ProviderId(PROVIDER), board.id, ThreadId(id)),
        originalPost =
            Post(
                id = PostId(id),
                board = board.id,
                threadId = ThreadId(id),
                isOriginalPost = true,
                subject = subject,
                comment = PostComment(raw = comment, nodes = persistentListOf()),
                attachments =
                    media?.let {
                        persistentListOf(
                            MediaAttachment(
                                id = "f$id",
                                originalFileName = "f$id",
                                extension = if (it == MediaType.VIDEO) "webm" else "jpg",
                                type = it,
                                sourceUrl = "https://example.invalid/$id",
                                thumbnailUrl = "https://example.invalid/${'$'}{id}s",
                                isSpoiler = spoiler,
                            ),
                        )
                    } ?: persistentListOf(),
            ),
        stats = ThreadStats(replyCount = replies, lastModifiedMillis = bumpedAt),
    )

    private companion object {
        const val PROVIDER = "fourchan"
        val TECH = Board(BoardId("g"), "Technology")
        val ANIME = Board(BoardId("a"), "Anime & Manga")
        const val COMMENT_ONLY = "no subject on this one, just an opening post that runs on a while"
        const val LONG_SUBJECT =
            "A subject long enough to need more than the two lines the row allows, so it has to " +
                "ellipsize somewhere and this is where we find out exactly where that is"
        const val UNBROKEN =
            "Supercalifragilisticexpialidociousaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val LONG_BOARD_TITLE = "Worksafe Gifs, with a title long enough to need truncating"
        const val FETCH_ERROR = "Could not reach the server"

        // Opaque mid-grey, so a tile that failed to draw is not mistaken for one that drew.
        const val TILE_COLOUR = 0xFF6E7A8A.toInt()

        // Wide enough to reach the right edge, so the scrollbar either clears the text or does not.
        const val SCROLLBAR_ROW =
            "A thread title wide enough to run all the way to the right edge of the row"
    }
}
