package com.orbin.ios

import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import coil3.ColorImage
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.request.ImageRequest
import coil3.size.Size
import coil3.test.FakeImageLoaderEngine
import com.orbin.core.model.BoardId
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Direct media destinations keep loading/error UI until their requested attachment is available. */
@OptIn(DelicateCoilApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class MediaDestinationContinuityTest {
    @get:Rule
    val compose = createComposeRule()

    private val images = CopyOnWriteArrayList<Any?>()

    @Before
    fun installImageTransport() {
        SingletonImageLoader.reset()
        val transport = FakeImageLoaderEngine.Builder().default(ColorImage(0xFF444444.toInt(), 640, 480)).build()
        SingletonImageLoader.setUnsafe { context ->
            ImageLoader
                .Builder(context)
                .components { add(transport) }
                .eventListener(
                    object : EventListener() {
                        override fun resolveSizeEnd(
                            request: ImageRequest,
                            size: Size,
                        ) {
                            images += request.data
                        }
                    },
                ).build()
        }
    }

    @After
    fun resetImageTransport() = SingletonImageLoader.reset()

    @Test
    fun aDelayedDirectMediaEntryKeepsItsCloseActionAndCancelsWhenReturningToFeed() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val engine =
                engine {
                    entered.complete(Unit)
                    try {
                        release.await()
                    } catch (error: CancellationException) {
                        cancelled.complete(Unit)
                        throw error
                    }
                    json(THREAD_JSON)
                }
            val browser = browser(backgroundScope, engine)
            browser.openMedia(THREAD, REPLY_ID, REPLY_THUMB)
            entered.await()
            val route = browser.backStack.value.last() as Route.Media
            val directStack = listOf(Route.Feed, route)
            assertEquals(directStack, browser.backStack.value)

            showDestination(browser, downloads(backgroundScope))
            compose.onNodeWithContentDescription("Loading media…").assertIsDisplayed()
            compose.onNodeWithContentDescription("Close").assertIsDisplayed()
            assertEquals(directStack, browser.backStack.value)
            assertTrue(browser.thread.value is Load.Loading)
            assertFalse(cancelled.isCompleted)

            compose.onNodeWithContentDescription("Close").performClick()
            cancelled.awaitFromEngine()
            compose.onNodeWithText("Feed resumed").assertIsDisplayed()
            assertEquals(listOf(Route.Feed), browser.backStack.value)
        }

    @Test
    fun aFailedDirectMediaEntryOffersRetryAndCloseInsteadOfDismissingItsEmptyPlaylist() =
        runTest {
            val attempts = AtomicInteger()
            val retryEntered = CompletableDeferred<Unit>()
            val retryRelease = CompletableDeferred<Unit>()
            val retryCancelled = CompletableDeferred<Unit>()
            val engine =
                engine {
                    if (attempts.incrementAndGet() == 1) {
                        respond("", HttpStatusCode.ServiceUnavailable)
                    } else {
                        retryEntered.complete(Unit)
                        try {
                            retryRelease.await()
                        } catch (error: CancellationException) {
                            retryCancelled.complete(Unit)
                            throw error
                        }
                        json(THREAD_JSON)
                    }
                }
            val browser = browser(backgroundScope, engine)
            browser.openMedia(THREAD, REPLY_ID, REPLY_THUMB)
            browser.thread.first { it is Load.Failed }
            val directStack = browser.backStack.value

            showDestination(browser, downloads(backgroundScope))
            compose.onNodeWithTag(ReaderStateTags.ERROR).assertIsDisplayed()
            compose.onNodeWithContentDescription("Close").assertIsDisplayed()
            assertEquals(2, directStack.size)
            assertEquals(Route.Feed, directStack.first())
            assertTrue(directStack.last() is Route.Media)
            assertEquals(directStack, browser.backStack.value)

            compose.onNodeWithTag(ReaderStateTags.ACTION).assertTextEquals("Retry").performClick()
            retryEntered.await()
            assertEquals(2, attempts.get())
            assertEquals(directStack, browser.backStack.value)
            compose.onNodeWithContentDescription("Loading media…").assertIsDisplayed()
            compose.onNodeWithContentDescription("Close").performClick()
            retryCancelled.awaitFromEngine()
            assertEquals(listOf(Route.Feed), browser.backStack.value)
            compose.onNodeWithText("Feed resumed").assertIsDisplayed()
        }

    @Test
    fun theAttachmentIdentityOpensTheReplyEvenWhenTheCatalogPreviewPointsElsewhere() =
        runTest {
            assertSelectedReply(backgroundScope, REPLY_ID, FIRST_THUMB)
        }

    @Test
    fun theCatalogThumbnailFindsTheReplyWhenItsCatalogAttachmentIdHasChanged() =
        runTest {
            assertSelectedReply(backgroundScope, "stale-catalog-id", REPLY_THUMB)
        }

    @Test
    fun aNewMediaEntryInTheSameLoadedThreadUsesItsOwnSelectionWithoutReloadingComments() =
        runTest {
            val threadRequests = AtomicInteger()
            val browser =
                browser(
                    backgroundScope,
                    engine {
                        threadRequests.incrementAndGet()
                        json(THREAD_JSON)
                    },
                )
            browser.openMedia(THREAD, REPLY_ID, REPLY_THUMB)
            val cached = browser.thread.first { it is Load.Ready }
            val replyRoute = browser.backStack.value.last() as Route.Media
            showDestination(browser, downloads(backgroundScope))
            compose
                .onNodeWithTag(MediaViewerTags.ACTIVE_PAGE, useUnmergedTree = true)
                .assertContentDescriptionEquals("reply.jpg")
            compose.onNodeWithText("2/2").assertIsDisplayed()

            compose.runOnIdle { browser.openMedia(THREAD, "100", FIRST_THUMB) }
            runCurrent()
            val openingRoute = browser.backStack.value.last() as Route.Media
            compose
                .onNodeWithTag(MediaViewerTags.ACTIVE_PAGE, useUnmergedTree = true)
                .assertContentDescriptionEquals("opening.jpg")
            compose.onNodeWithText("1/2").assertIsDisplayed()
            assertEquals(listOf(Route.Feed, replyRoute, openingRoute), browser.backStack.value)
            assertFalse(browser.threadRefreshing.value)
            assertSame(cached, browser.thread.value)
            assertEquals(1, threadRequests.get())

            compose.onNodeWithContentDescription("Close").performClick()
            runCurrent()
            compose
                .onNodeWithTag(MediaViewerTags.ACTIVE_PAGE, useUnmergedTree = true)
                .assertContentDescriptionEquals("reply.jpg")
            compose.onNodeWithText("2/2").assertIsDisplayed()
            assertEquals(listOf(Route.Feed, replyRoute), browser.backStack.value)
            assertSame(cached, browser.thread.value)
            assertEquals(1, threadRequests.get())

            compose.onNodeWithContentDescription("Close").performClick()
            assertEquals(listOf(Route.Feed), browser.backStack.value)
            compose.onNodeWithText("Feed resumed").assertIsDisplayed()
        }

    private suspend fun assertSelectedReply(
        scope: CoroutineScope,
        attachmentId: String,
        thumbnailUrl: String,
    ) {
        val browser = browser(scope, engine { json(THREAD_JSON) })
        browser.openMedia(THREAD, attachmentId, thumbnailUrl)
        browser.thread.first { it is Load.Ready }
        val route = browser.backStack.value.last() as Route.Media
        assertEquals(listOf(Route.Feed, route), browser.backStack.value)
        showDestination(browser, downloads(scope))

        compose
            .onNodeWithTag(MediaViewerTags.ACTIVE_PAGE, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("reply.jpg")
        compose.onNodeWithText("2/2").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 5_000) { REPLY_SOURCE in images }
        assertEquals(
            REPLY_SOURCE,
            images.first { it == FIRST_SOURCE || it == REPLY_SOURCE },
        )
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(listOf(Route.Feed), browser.backStack.value)
    }

    private fun showDestination(
        browser: Browser,
        downloads: MediaDownloads,
    ) {
        compose.setContent {
            val stack by browser.backStack.collectAsState()
            NextTheme(platform = NextPlatform.IOS) {
                val route = stack.lastOrNull() as? Route.Media
                if (route == null) {
                    Text("Feed resumed")
                } else {
                    MediaDestination(browser, downloads, route)
                }
            }
        }
    }

    private suspend fun browser(
        scope: CoroutineScope,
        engine: MockEngine,
    ): Browser {
        val settings = FakeSettings().apply { settings.value = settings.value.copy(onboardingCompleted = true) }
        settings.setPlaybackSettings(PlaybackScope(), PlaybackSettings(autoplay = false, preloadNextClip = false))
        return Browser(
            orbinProviders(iosHttpClient(engine)).filter { it.metadata.id == FOURCHAN },
            FakeBookmarks(),
            FakeHistory(),
            FakeBoardPreferences().apply { subscribed.value = mapOf(FOURCHAN to setOf(BOARD)) },
            settings,
            scope,
        ).also { browser ->
            browser.firstRun.state.first { state -> state.initialized && !state.needed }
            browser.settings.current.first { current ->
                val playback = current.playbackPreferences.resolve(FOURCHAN, BOARD)
                !playback.autoplay && !playback.preloadNextClip
            }
        }
    }

    private fun downloads(scope: CoroutineScope) =
        MediaDownloads(
            dao = EmptyDestinationDownloads,
            store = { _, _, _, _ -> error("Opening media must not save it") },
            fetch = { _, _ -> error("Opening media must not start a download") },
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

    private suspend fun CompletableDeferred<Unit>.awaitFromEngine() {
        // Ktor signals cancellation from its own dispatcher, outside the virtual test clock.
        withContext(Dispatchers.Default) { withTimeout(5_000) { await() } }
    }

    private companion object {
        val FOURCHAN = ProviderId("fourchan")
        val BOARD = BoardId("g")
        val THREAD = ThreadKey(FOURCHAN, BOARD, ThreadId(7))
        const val REPLY_ID = "200"
        const val FIRST_SOURCE = "https://i.4cdn.org/g/100.jpg"
        const val FIRST_THUMB = "https://i.4cdn.org/g/100s.jpg"
        const val REPLY_SOURCE = "https://i.4cdn.org/g/200.jpg"
        const val REPLY_THUMB = "https://i.4cdn.org/g/200s.jpg"
        const val BOARDS_JSON = """{"boards":[{"board":"g","title":"Technology","ws_board":1}]}"""
        const val THREAD_JSON =
            """{"posts":[{"no":7,"sub":"Thread images","time":1,"tim":100,"filename":"opening","ext":".jpg","w":640,"h":480},{"no":8,"resto":7,"time":2,"tim":200,"filename":"reply","ext":".jpg","w":640,"h":480}]}"""
    }
}

/** No download side effect is permitted while a media destination loads or closes. */
private object EmptyDestinationDownloads : DownloadDao {
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
