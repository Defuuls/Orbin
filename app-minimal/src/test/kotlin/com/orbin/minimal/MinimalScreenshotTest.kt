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
import com.orbin.core.designsystem.theme.OrbinPreviewTheme
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
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
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class MinimalScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

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

    @Test
    fun boardsPopulated() =
        capture("minimal_boards_populated") {
            MinimalBoardsContent(boards = sampleBoards(), onBack = {}, onToggle = { _, _ -> })
        }

    /**
     * The picker with nothing in it. The screen cannot tell "still fetching" from "this provider
     * returned no boards", so both land here.
     */
    @Test
    fun boardsEmpty() =
        capture("minimal_boards_empty") {
            MinimalBoardsContent(boards = emptyList(), onBack = {}, onToggle = { _, _ -> })
        }

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

        // Wide enough to reach the right edge, so the scrollbar either clears the text or does not.
        const val SCROLLBAR_ROW =
            "A thread title wide enough to run all the way to the right edge of the row"
    }
}
