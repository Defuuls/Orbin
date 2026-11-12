package com.orbin.ios

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
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
import com.orbin.core.model.CatalogMode
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.graph.orbinProviders
import com.orbin.uinext.NextPlatform
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ReaderFeedTags
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/** The production iOS Home binding uses durable reader preferences and explicit media navigation. */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedDestinationContinuityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val requestedImages = CopyOnWriteArrayList<Any?>()

    @Before
    fun installThumbnailLoader() {
        SingletonImageLoader.reset()
        val engine = FakeImageLoaderEngine.Builder().default(ColorImage(Color.Red.toArgb(), 500, 500)).build()
        SingletonImageLoader.setUnsafe { context ->
            ImageLoader
                .Builder(context)
                .components { add(engine) }
                .eventListener(
                    object : EventListener() {
                        override fun resolveSizeEnd(
                            request: ImageRequest,
                            size: Size,
                        ) {
                            requestedImages += request.data
                        }
                    },
                ).build()
        }
    }

    @After
    fun resetThumbnailLoader() {
        Pencil.hoverAt(null)
        SingletonImageLoader.reset()
    }

    @Test
    fun storedCatalogModeCanBeChangedFromHomeAndSurvivesRemounting() =
        runTest {
            val repository =
                FakeSettings().apply {
                    settings.value = settings.value.copy(onboardingCompleted = true, catalogMode = CatalogMode.GRID)
                }
            val browser = loadedBrowser(repository, backgroundScope)
            var visible by mutableStateOf(true)
            composeRule.setContent {
                NextTheme(platform = NextPlatform.IOS) {
                    if (visible) FeedDestination(browser)
                }
            }
            composeRule.onNodeWithTag(ReaderFeedTags.GRID).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("More options").performClick()
            composeRule.onNodeWithText("List view").performClick()
            runCurrent()
            assertEquals(CatalogMode.LIST, repository.settings.value.catalogMode)

            composeRule.runOnIdle { visible = false }
            composeRule.waitForIdle()
            composeRule.runOnIdle { visible = true }
            composeRule.onNodeWithTag(ReaderFeedTags.GRID).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("More options").performClick()
            composeRule.onNodeWithText("Grid view").assertIsDisplayed()
        }

    @Test
    fun theBoundedThumbnailOpensThreadVideosEvenWhenCommentsAreEnabled() =
        runTest {
            val repository =
                FakeSettings().apply {
                    settings.value = settings.value.copy(onboardingCompleted = true, openThreadComments = true)
                }
            val browser = loadedBrowser(repository, backgroundScope)
            val entry = (browser.feed.value as Load.Ready).value.single()
            val attachment =
                entry.thread.originalPost.attachments
                    .single()
            showHome(browser)

            composeRule.waitUntil(timeoutMillis = 5_000) { requestedImages.isNotEmpty() }
            assertTrue(requestedImages.all { it == attachment.thumbnailUrl })
            assertNotEquals(attachment.sourceUrl, attachment.thumbnailUrl)
            composeRule.onNodeWithTag(ReaderFeedTags.thumbnail(entry.feedRowId), useUnmergedTree = true).performClick()
            assertEquals(Route.ThreadVideos(THREAD), browser.backStack.value.last())
            assertEquals(listOf(Route.Feed, Route.ThreadVideos(THREAD)), browser.backStack.value)
        }

    @Test
    fun tappingTheReaderRowStillOpensItsThreadWhenTheChatPreferenceUsesVideos() =
        runTest {
            val repository =
                FakeSettings().apply {
                    settings.value = settings.value.copy(onboardingCompleted = true, openThreadComments = false)
                }
            val browser = loadedBrowser(repository, backgroundScope)
            val entry = (browser.feed.value as Load.Ready).value.single()
            showHome(browser)

            composeRule.onNodeWithTag(ReaderFeedTags.row(entry.feedRowId)).performClick()
            assertEquals(Route.ThreadPage(THREAD), browser.backStack.value.last())
        }

    private fun showHome(browser: Browser) {
        composeRule.setContent {
            NextTheme(platform = NextPlatform.IOS) { FeedDestination(browser) }
        }
        composeRule.onNodeWithTag(ReaderFeedTags.GRID).assertIsDisplayed()
        composeRule.onNodeWithTag("tiktok_feed_pager").assertDoesNotExist()
        composeRule.onNodeWithTag("tiktok_feed_actions").assertDoesNotExist()
    }

    private suspend fun loadedBrowser(
        repository: FakeSettings,
        scope: CoroutineScope,
    ): Browser {
        val engine =
            MockEngine { request ->
                val json =
                    when (request.url.encodedPath) {
                        "/boards.json" -> BOARDS
                        "/g/catalog.json" -> CATALOG
                        else -> "[]"
                    }
                respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        val browser =
            Browser(
                orbinProviders(iosHttpClient(engine)).filter { it.metadata.id == FOURCHAN },
                FakeBookmarks(),
                FakeHistory(),
                FakeBoardPreferences().apply { subscribed.value = mapOf(FOURCHAN to setOf(BOARD)) },
                repository,
                scope,
            )
        browser.firstRun.state.first { it.initialized && !it.needed }
        browser.feed.first { it is Load.Ready && it.value.size == 1 }
        return browser
    }

    private companion object {
        val FOURCHAN = ProviderId("fourchan")
        val BOARD = BoardId("g")
        val THREAD = ThreadKey(FOURCHAN, BOARD, ThreadId(7))
        const val BOARDS = """{"boards":[{"board":"g","title":"Technology","ws_board":1}]}"""
        const val CATALOG =
            """[{"page":1,"threads":[{"no":7,"sub":"Reader feed clip","time":1,"tim":7,"ext":".webm","filename":"clip","w":640,"h":360,"replies":3,"images":2}]}]"""
    }
}
