package com.orbin.ios

import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.HomeFeedMode
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.graph.orbinProviders
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Source filtering, five-tab navigation and local collections across both providers. */
class BrowserSocialTest {
    private val requested = mutableListOf<String>()
    private val recording = Mutex()
    private val bookmarks = FakeBookmarks()
    private val history = FakeHistory()
    private val boardPreferences = FakeBoardPreferences()

    // Past first run: these tests are about browsing, not the board picker.
    private val settings = FakeSettings().apply { settings.value = settings.value.copy(onboardingCompleted = true) }

    private fun browser(
        scope: CoroutineScope,
        routes: Map<String, String?>,
    ): Browser {
        val engine =
            MockEngine { request ->
                // The engine answers on several threads at once (both sites load together).
                recording.withLock {
                    requested += request.url.toString()
                }
                reply(request, routes)
            }
        return Browser(
            orbinProviders(iosHttpClient(engine)),
            bookmarks,
            history,
            boardPreferences,
            settings,
            scope,
            now = { NOW },
        )
    }

    private fun MockRequestHandleScope.reply(
        request: HttpRequestData,
        routes: Map<String, String?>,
    ): HttpResponseData {
        val body = routes["${request.url.host}${request.url.encodedPath}"]
        return if (body == null) {
            respond("", HttpStatusCode.NotFound)
        } else {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    }

    private suspend fun <T> StateFlow<Load<T>>.settled(): Load<T> = first { it !is Load.Loading }

    private suspend fun Browser.finishSetupIfNeeded() {
        if (firstRun.state.first { it.initialized }.needed) {
            firstRun.state.first { it.canStart }
            completeFirstRun().join()
        }
    }

    private fun Load<List<FeedThread>>.boardIds(): List<String> =
        assertIs<Load.Ready<List<FeedThread>>>(this).value.map { it.thread.key.board.value }

    @Test
    fun theCombinedFeedIncludesBothSitesAndFiltersWithoutChangingTheBoardDirectory() =
        runTest {
            val browser = browser(backgroundScope, BOTH_SITES + LYNXCHAN_CATALOG)
            val boards = assertIs<Load.Ready<List<SiteBoard>>>(browser.boards.settled()).value
            browser.selectFeedMode(HomeFeedMode.FOLLOWING)
            boards.forEach { browser.setFollowed(it, follow = true) }
            browser.finishSetupIfNeeded()

            val first =
                browser.feed.first {
                    it is Load.Ready &&
                        it.value
                            .map { row -> row.provider }
                            .toSet()
                            .size == 2
                }
            assertEquals(setOf("g", "b"), first.boardIds().toSet())
            assertEquals(Route.Feed, browser.backStack.value.single(), "the feed is the start screen")
            assertNull(browser.feedSite.value)

            browser.selectFeedSite(ProviderId("bbwchan"))

            val second =
                browser.feed.first { load ->
                    load is Load.Ready && load.value.isNotEmpty() && load.value.all { it.provider.value == "bbwchan" }
                }
            assertEquals(listOf("b"), second.boardIds())
            assertEquals(
                "fourchan",
                browser.activeSite.value.value,
                "Home filtering does not switch the Boards directory",
            )

            browser.selectFeedSite(null)
            val combined =
                browser.feed.first {
                    it is Load.Ready &&
                        it.value
                            .map { row -> row.provider }
                            .toSet()
                            .size == 2
                }
            assertEquals(setOf("g", "b"), combined.boardIds().toSet())
        }

    @Test
    fun theFiveTabsReplaceTheStackAndSettingsReturnsToTheSelectedTab() =
        runTest {
            val browser = browser(backgroundScope, BOTH_SITES)
            for (tab in listOf(Route.Feed, Route.Boards, Route.Watched, Route.Search, Route.Saved)) {
                browser.openTab(tab)
                browser.open(Route.Settings)
                assertEquals(listOf(tab, Route.Settings), browser.backStack.value)
                assertTrue(browser.back())
                assertEquals(listOf(tab), browser.backStack.value)
            }
        }

    @Test
    fun savedAndHistoryKeepIdenticalThreadNumbersFromDifferentSites() =
        runTest {
            val browser = browser(backgroundScope, BOTH_SITES)
            val keys = listOf("fourchan", "bbwchan").map { ThreadKey(ProviderId(it), BoardId("g"), ThreadId(7)) }
            keys.forEach { key ->
                bookmarks.addBookmark(Bookmark(key, key.provider.value, createdAtMillis = NOW))
                history.record(
                    com.orbin.core.model
                        .HistoryEntry(key, key.provider.value, lastVisitedMillis = NOW),
                )
            }
            assertEquals(
                keys.toSet(),
                browser.savedThreads
                    .first()
                    .map { it.key }
                    .toSet(),
            )
            assertEquals(
                keys.toSet(),
                browser.readingHistory
                    .first()
                    .map { it.key }
                    .toSet(),
            )
            browser.openTab(Route.Saved)
            browser.openThread(keys.last())
            assertEquals(keys.last(), assertIs<Route.ThreadPage>(browser.backStack.value.last()).key)
        }

    @Test
    fun feedActionsSaveWatchAndUnwatchWithoutFetchingTheFullThread() =
        runTest {
            val browser = browser(backgroundScope, BOTH_SITES)
            val board = assertIs<Load.Ready<List<SiteBoard>>>(browser.boards.settled()).value.first()
            browser.selectFeedMode(HomeFeedMode.FOLLOWING)
            browser.setFollowed(board, true)
            browser.finishSetupIfNeeded()
            val feed = browser.feed.first { it is Load.Ready && it.value.isNotEmpty() }
            val row = assertIs<Load.Ready<List<FeedThread>>>(feed).value.first()
            browser.toggleFeedSaved(row)
            assertEquals(false, bookmarks.observeBookmark(row.thread.key).first { it != null }?.isWatched)
            browser.toggleFeedWatch(row)
            bookmarks.observeBookmark(row.thread.key).first { it?.isWatched == true }
            browser.toggleFeedSaved(row)
            assertTrue(bookmarks.getBookmark(row.thread.key)?.isWatched == true)
            browser.toggleFeedWatch(row)
            assertEquals(
                row.thread.key,
                bookmarks.observeBookmark(row.thread.key).first { it?.isWatched == false }?.key,
            )
            assertTrue(requested.none { "/thread/" in it }, "feed actions use the catalog's identity")
            browser.toggleFeedSaved(row)
            assertNull(bookmarks.observeBookmark(row.thread.key).first { it == null })
        }

    @Test
    fun favoriteBoardsStayIndependentOfHomeSubscriptions() =
        runTest {
            val browser = browser(backgroundScope, BOTH_SITES)
            val board = assertIs<Load.Ready<List<SiteBoard>>>(browser.boards.settled()).value.first()
            browser.selectFeedMode(HomeFeedMode.FOLLOWING)
            browser.setFavorite(board, true)
            val key = FollowedBoard(board.provider, board.board.id)
            browser.favorites.first { key in it }
            val setup = browser.firstRun.state.first { it.initialized }
            assertTrue(setup.needed)
            assertFalse(setup.canStart)
            assertTrue(browser.followed.value.isEmpty())
            assertEquals(Load.Ready(emptyList()), browser.feed.settled())

            browser.setFollowed(board, true)
            browser.finishSetupIfNeeded()
            browser.followed.first { it.isNotEmpty() }
            browser.feed.first { it is Load.Ready && it.value.isNotEmpty() }
            browser.setFavorite(board, false)
            browser.favorites.first { it.isEmpty() }
            assertTrue(key in browser.followed.value)
            assertEquals(listOf(board.board.id.value), browser.feed.value.boardIds())
        }

    private companion object {
        val LYNXCHAN_CATALOG = "bbw-chan.link/b/catalog.json" to """[{"threadId":5,"subject":"Hi"}]"""
        const val NOW = 1_700_000_000_000L

        const val LYNXCHAN_BOARDS = "bbw-chan.link/boards.js"
        val BOTH_SITES =
            mapOf(
                "a.4cdn.org/boards.json" to """{"boards":[{"board":"g","title":"Technology"}]}""",
                "a.4cdn.org/g/catalog.json" to
                    """[{"page":1,"threads":[{"no":7,"sub":"Hi","time":1,"tim":7,"ext":".mp4"}]}]""",
                "a.4cdn.org/g/thread/7.json" to
                    """{"posts":[{"no":7,"sub":"Hi","time":1,"tim":7,"ext":".mp4"}]}""",
                LYNXCHAN_BOARDS to """{"status":"ok","data":{"boards":[{"boardUri":"b","boardName":"Random"}]}}""",
            )
    }
}
