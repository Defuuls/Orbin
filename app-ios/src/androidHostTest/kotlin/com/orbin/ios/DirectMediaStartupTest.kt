package com.orbin.ios

import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.test.FakeImageLoaderEngine
import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.PlaybackSettings
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.database.entity.DownloadEntity
import com.orbin.graph.orbinProviders
import com.orbin.uinext.NextPlatform
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ReaderStateTags
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/** A selected catalog clip can own its player before comments finish loading. */
@OptIn(DelicateCoilApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class DirectMediaStartupTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun installImageTransport() {
        SingletonImageLoader.reset()
        val transport = FakeImageLoaderEngine.Builder().default(ColorImage(0xFF444444.toInt(), 640, 480)).build()
        SingletonImageLoader.setUnsafe { context -> ImageLoader.Builder(context).components { add(transport) }.build() }
    }

    @After
    fun resetImageTransport() = SingletonImageLoader.reset()

    @Test
    fun selectedVideoStartsBeforeThreadResponseAndKeepsItsIdentityWhenThePlaylistExpands() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val browser =
                browser(
                    backgroundScope,
                    engine {
                        entered.complete(Unit)
                        release.await()
                        json(THREAD_JSON)
                    },
                )
            browser.openMedia(THREAD, SELECTED)
            entered.await()
            val route = browser.backStack.value.last() as Route.Media
            val initialKey = mediaViewerKeys(route.viewerFiles(emptyList())).single()
            showDestination(browser, downloads(backgroundScope))

            assertTrue(browser.thread.value is Load.Loading)
            assertSelectedPlayer(SELECTED.originalFileName, "1/1")
            compose.onNodeWithContentDescription("Loading media…").assertDoesNotExist()

            release.complete(Unit)
            val loaded = (browser.thread.first { it is Load.Ready } as Load.Ready).value
            val expanded = route.viewerFiles(loaded.files)
            assertEquals(2, expanded.size)
            assertEquals(1, route.startIndexIn(expanded))
            assertEquals(SELECTED.sourceUrl, expanded[1].sourceUrl)
            assertEquals(initialKey, mediaViewerKeys(expanded)[1])
            assertSelectedPlayer("reply-video.mp4", "2/2")
            assertEquals(listOf(Route.Feed, route), browser.backStack.value)
        }

    @Test
    fun failedThreadRequestLeavesTheAlreadySelectedVideoOpen() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val browser =
                browser(
                    backgroundScope,
                    engine {
                        entered.complete(Unit)
                        release.await()
                        respond("", HttpStatusCode.ServiceUnavailable)
                    },
                )
            browser.openMedia(THREAD, SELECTED)
            entered.await()
            val route = browser.backStack.value.last() as Route.Media
            showDestination(browser, downloads(backgroundScope))
            assertSelectedPlayer(SELECTED.originalFileName, "1/1")

            release.complete(Unit)
            browser.thread.first { it is Load.Failed }
            assertSelectedPlayer(SELECTED.originalFileName, "1/1")
            compose.onNodeWithTag(ReaderStateTags.ERROR).assertDoesNotExist()
            compose.onNodeWithContentDescription("Close").assertIsDisplayed()
            assertEquals(listOf(Route.Feed, route), browser.backStack.value)
        }

    @Test
    fun anAuthoritativelyBlockedThreadStopsTheSeedPlayerWhenItsResponseArrives() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val retryEntered = CompletableDeferred<Unit>()
            val retryRelease = CompletableDeferred<Unit>()
            val attempts = AtomicInteger()
            val browser =
                browser(
                    backgroundScope,
                    engine {
                        if (attempts.incrementAndGet() == 1) {
                            entered.complete(Unit)
                            release.await()
                            json(THREAD_JSON.replace("Thread files", "gore video"))
                        } else {
                            retryEntered.complete(Unit)
                            retryRelease.await()
                            json(THREAD_JSON)
                        }
                    },
                )
            browser.openMedia(THREAD, SELECTED)
            entered.await()
            showDestination(browser, downloads(backgroundScope))
            assertSelectedPlayer(SELECTED.originalFileName, "1/1")

            release.complete(Unit)
            val failed = browser.thread.first { it is Load.Failed } as Load.Failed
            assertTrue(failed.contentBlocked)
            compose.onAllNodesWithTag(MediaViewerTags.PLAYER, useUnmergedTree = true).assertCountEquals(0)
            compose.onNodeWithTag(ReaderStateTags.ERROR).assertIsDisplayed()
            compose.onNodeWithContentDescription("Close").assertIsDisplayed()

            compose.onNodeWithTag(ReaderStateTags.ACTION).performClick()
            retryEntered.await()
            assertTrue(browser.thread.value is Load.Loading)
            compose.onAllNodesWithTag(MediaViewerTags.PLAYER, useUnmergedTree = true).assertCountEquals(0)
            compose.onNodeWithContentDescription("Loading media…").assertIsDisplayed()
            compose.onNodeWithContentDescription("Close").assertIsDisplayed()

            retryRelease.complete(Unit)
            browser.thread.first { it is Load.Ready }
            assertSelectedPlayer("reply-video.mp4", "2/2")
            assertEquals(2, attempts.get())
        }

    private fun assertSelectedPlayer(
        name: String,
        position: String,
    ) {
        compose.onAllNodesWithTag(MediaViewerTags.PLAYER, useUnmergedTree = true).assertCountEquals(1)
        compose
            .onNodeWithTag(MediaViewerTags.ACTIVE_PAGE, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertContentDescriptionEquals(name)
        compose.onNodeWithText(position).assertIsDisplayed()
    }

    private fun showDestination(
        browser: Browser,
        downloads: MediaDownloads,
    ) {
        compose.setContent {
            val stack by browser.backStack.collectAsState()
            NextTheme(platform = NextPlatform.IOS) {
                val route = stack.lastOrNull() as? Route.Media
                if (route == null) Text("Feed resumed") else MediaDestination(browser, downloads, route)
            }
        }
    }

    private suspend fun browser(
        scope: CoroutineScope,
        engine: MockEngine,
    ): Browser {
        val settings = FakeSettings().apply { settings.value = settings.value.copy(onboardingCompleted = true) }
        settings.setPlaybackSettings(PlaybackScope(), PlaybackSettings(autoplay = true, preloadNextClip = false))
        return Browser(
            orbinProviders(iosHttpClient(engine)).filter { it.metadata.id == FOURCHAN },
            FakeBookmarks(),
            FakeHistory(),
            FakeBoardPreferences().apply { subscribed.value = mapOf(FOURCHAN to setOf(BOARD)) },
            settings,
            scope,
        ).also { browser ->
            browser.firstRun.state.first { it.initialized && !it.needed }
            browser.settings.current.first { !it.playbackPreferences.resolve(FOURCHAN, BOARD).preloadNextClip }
        }
    }

    private fun downloads(scope: CoroutineScope) =
        MediaDownloads(
            dao = EmptyStartupDownloads,
            store = { _, _, _, _ -> error("Opening media must not save it") },
            fetch = { _, _ -> error("Opening media must not download it") },
            scope = scope,
        )

    private fun engine(onThread: suspend MockRequestHandleScope.() -> HttpResponseData) =
        MockEngine { request ->
            when (request.url.encodedPath) {
                "/g/thread/7.json" -> onThread(this)
                "/boards.json" -> json(BOARDS_JSON)
                else -> json("[]")
            }
        }

    private fun MockRequestHandleScope.json(body: String): HttpResponseData =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private companion object {
        val FOURCHAN = ProviderId("fourchan")
        val BOARD = BoardId("g")
        val THREAD = ThreadKey(FOURCHAN, BOARD, ThreadId(7))
        val SELECTED =
            MediaAttachment(
                id = "catalog-video-200",
                originalFileName = "selected-video.mp4",
                extension = "mp4",
                type = MediaType.VIDEO,
                sourceUrl = "https://i.4cdn.org/g/200.mp4",
                thumbnailUrl = "https://i.4cdn.org/g/200s.jpg",
            )
        const val BOARDS_JSON = """{"boards":[{"board":"g","title":"Technology","ws_board":1}]}"""
        const val THREAD_JSON =
            """{"posts":[{"no":7,"sub":"Thread files","time":1,"tim":100,"filename":"opening","ext":".jpg","w":640,"h":480},{"no":8,"resto":7,"time":2,"tim":200,"filename":"reply-video","ext":".mp4","w":640,"h":480}]}"""
    }
}

private object EmptyStartupDownloads : DownloadDao {
    override fun observeAll(): Flow<List<DownloadEntity>> = flowOf(emptyList())

    override suspend fun all(): List<DownloadEntity> = emptyList()

    override suspend fun upsert(entry: DownloadEntity): Unit = error("Unexpected download write")

    override suspend fun updateStatus(
        id: Long,
        status: String,
    ): Unit = error("Unexpected download write")

    override suspend fun clear(): Unit = error("Unexpected download clear")

    override suspend fun getById(id: Long): DownloadEntity? = null

    override suspend fun findByTarget(
        url: String,
        fileName: String,
        relativeDir: String,
    ): List<DownloadEntity> = emptyList()

    override suspend fun delete(id: Long): Unit = error("Unexpected download delete")
}
