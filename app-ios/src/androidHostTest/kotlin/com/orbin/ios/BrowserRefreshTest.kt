package com.orbin.ios

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogSort
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.HomeFeedMode
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.graph.orbinProviders
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Refreshes preserve the current reader, while a different identity clears the old content. */
class BrowserRefreshTest {
    @Test
    fun failedCatalogRefreshKeepsLoadedRowsAndRetryPublishesTheNewCatalog() =
        runTest {
            val fixture = Fixture()
            val browser = fixture.browser(backgroundScope)
            val board = browser.board(FOURCHAN)
            browser.openBoard(board)
            val loaded = assertIs<Load.Ready<List<CatalogThread>>>(browser.catalog.settled())
            val preparedRows = browser.preparedCatalogRows.value
            assertEquals(loaded.value.map { it.key }.toSet(), preparedRows.keys)
            val failedRequest = fixture.gate(FOURCHAN_CATALOG)

            browser.retry()
            failedRequest.entered.await()

            assertTrue(browser.catalogRefreshing.value)
            assertSame(
                loaded,
                browser.catalog.value,
                "Refreshing must preserve the loaded catalog and its scroll owner",
            )
            assertNull(browser.catalogRefreshFailure.value)
            assertSame(preparedRows, browser.preparedCatalogRows.value)

            failedRequest.response.complete(Reply(status = HttpStatusCode.ServiceUnavailable))
            browser.catalogRefreshFailure.first { it != null }
            browser.catalogRefreshing.first { !it }

            assertSame(loaded, browser.catalog.value, "A failed refresh must leave the previous rows visible")
            assertSame(preparedRows, browser.preparedCatalogRows.value)
            val retry = fixture.gate(FOURCHAN_CATALOG)
            browser.retry()
            retry.entered.await()
            assertTrue(browser.catalogRefreshing.value)
            assertNull(browser.catalogRefreshFailure.value, "Retry dismisses the previous refresh feedback")
            assertSame(loaded, browser.catalog.value)

            retry.response.complete(Reply(catalog(8)))
            browser.catalogRefreshing.first { !it }

            assertEquals(
                listOf(8L),
                assertIs<Load.Ready<List<CatalogThread>>>(browser.catalog.value).value.map { it.key.thread.value },
            )
            assertNull(browser.catalogRefreshFailure.value)
            assertEquals(
                listOf(8L),
                browser.preparedCatalogRows.value.keys
                    .map { it.thread.value },
                "Prepared row identities must be published with the replacement catalog",
            )
        }

    @Test
    fun theSameBoardIdOnAnotherSiteCannotShowThePreviousSitesCatalogWhileLoading() =
        runTest {
            val fixture = Fixture()
            val browser = fixture.browser(backgroundScope)
            val first = browser.board(FOURCHAN)
            val second = browser.board(BBWCHAN)
            assertEquals(first.board.id, second.board.id, "The regression requires matching board ids across sites")
            browser.openBoard(first)
            browser.catalog.settled()
            val request = fixture.gate(BBWCHAN_CATALOG)

            browser.openBoard(second)
            request.entered.await()

            browser.setReaderPanesVisible(
                BoardReaderRoute(Route.Catalog(first)),
                wideWindow = false,
                catalogVisible = false,
                threadVisible = false,
            )
            browser.refreshCatalog(first)

            assertFalse(request.cancelled.isCompleted, "Disposing the previous reader must not cancel the new source")
            assertIs<Load.Loading>(browser.catalog.value)
            assertFalse(browser.catalogRefreshing.value, "A new identity is an initial load, not a refresh")
            assertNull(browser.catalogRefreshFailure.value)
            assertTrue(browser.preparedCatalogRows.value.isEmpty(), "Prepared rows from the previous site must clear")

            request.response.complete(Reply("""[{"threadId":19,"subject":"Other site"}]"""))
            val loaded = assertIs<Load.Ready<List<CatalogThread>>>(browser.catalog.settled()).value
            assertEquals(listOf(BBWCHAN), loaded.map { it.key.provider })
            assertEquals(listOf(19L), loaded.map { it.key.thread.value })
            assertEquals(loaded.map { it.key }.toSet(), browser.preparedCatalogRows.value.keys)
        }

    @Test
    fun failedThreadRefreshKeepsLoadedPostsAndRetryPublishesTheNewThread() =
        runTest {
            val fixture = Fixture()
            val browser = fixture.browser(backgroundScope)
            browser.firstRun.state.first { it.initialized && !it.needed }
            val key = ThreadKey(FOURCHAN, BoardId("x"), ThreadId(7))
            browser.openThread(key)
            val loaded = assertIs<Load.Ready<Thread>>(browser.thread.settled())
            val prepared = assertNotNull(browser.preparedThread.value)
            assertSame(loaded.value, prepared.thread, "Loaded content and prepared presentation publish together")
            val failedRequest = fixture.gate(FOURCHAN_THREAD)

            browser.retry()
            failedRequest.entered.await()

            assertTrue(browser.threadRefreshing.value)
            assertSame(loaded, browser.thread.value, "Refreshing must preserve the loaded posts and their scroll owner")
            assertNull(browser.threadRefreshFailure.value)
            assertSame(prepared, browser.preparedThread.value)

            failedRequest.response.complete(Reply(status = HttpStatusCode.ServiceUnavailable))
            browser.threadRefreshFailure.first { it != null }
            browser.threadRefreshing.first { !it }
            assertSame(loaded, browser.thread.value)
            assertSame(
                prepared,
                browser.preparedThread.value,
                "Failure must preserve the current prepared presentation",
            )

            val retry = fixture.gate(FOURCHAN_THREAD)
            browser.retry()
            retry.entered.await()
            assertNull(browser.threadRefreshFailure.value)
            assertSame(loaded, browser.thread.value)
            assertSame(prepared, browser.preparedThread.value)
            retry.response.complete(Reply(thread(7, "Updated thread")))
            browser.threadRefreshing.first { !it }

            assertEquals("Updated thread", assertIs<Load.Ready<Thread>>(browser.thread.value).value.subject)
            assertNull(browser.threadRefreshFailure.value)
            val updated = assertNotNull(browser.preparedThread.value)
            assertNotSame(prepared, updated)
            assertSame(assertIs<Load.Ready<Thread>>(browser.thread.value).value, updated.thread)
        }

    @Test
    fun anotherThreadCannotShowThePreviousThreadsPostsWhileLoading() =
        runTest {
            val fixture = Fixture()
            val browser = fixture.browser(backgroundScope)
            browser.firstRun.state.first { it.initialized && !it.needed }
            browser.openThread(ThreadKey(FOURCHAN, BoardId("x"), ThreadId(7)))
            browser.thread.settled()
            val next = ThreadKey(FOURCHAN, BoardId("x"), ThreadId(8))
            val request = fixture.gate("a.4cdn.org/x/thread/8.json")

            browser.openThread(next)
            request.entered.await()

            assertIs<Load.Loading>(browser.thread.value)
            assertFalse(browser.threadRefreshing.value)
            assertNull(browser.threadRefreshFailure.value)
            assertNull(browser.preparedThread.value, "A new thread must not inherit the previous presentation")

            request.response.complete(Reply(thread(8, "Next thread")))
            assertEquals(next, assertIs<Load.Ready<Thread>>(browser.thread.settled()).value.key)
            assertEquals(next, assertNotNull(browser.preparedThread.value).thread.key)
        }

    @Test
    fun refreshingTheAdjacentCatalogDoesNotCancelTheThreadLoadingBesideIt() =
        runTest {
            val fixture = Fixture()
            val browser = fixture.browser(backgroundScope)
            val board = browser.board(FOURCHAN)
            browser.openBoard(board)
            val loadedCatalog = assertIs<Load.Ready<List<CatalogThread>>>(browser.catalog.settled())
            browser.setReaderPanesVisible(
                BoardReaderRoute(Route.Catalog(board)),
                wideWindow = true,
                catalogVisible = true,
                threadVisible = false,
            )
            val key = ThreadKey(FOURCHAN, board.board.id, ThreadId(7))
            val threadRequest = fixture.gate(FOURCHAN_THREAD)
            browser.openThread(key)
            threadRequest.entered.await()
            assertIs<Load.Loading>(browser.thread.value)
            val catalogRequest = fixture.gate(FOURCHAN_CATALOG)

            browser.refreshCatalog(board)
            catalogRequest.entered.await()

            assertEquals(Route.ThreadPage(key), browser.backStack.value.last())
            assertTrue(browser.catalogRefreshing.value)
            assertSame(loadedCatalog, browser.catalog.value)
            assertIs<Load.Loading>(browser.thread.value)
            threadRequest.response.complete(Reply(thread(7, "Adjacent thread")))
            val loadedThread = assertIs<Load.Ready<Thread>>(browser.thread.settled()).value

            assertEquals(key, loadedThread.key)
            assertEquals("Adjacent thread", loadedThread.subject)
            assertSame(loadedThread, assertNotNull(browser.preparedThread.value).thread)
            assertTrue(browser.catalogRefreshing.value, "Finishing the thread must not stop the catalog refresh")
            assertSame(loadedCatalog, browser.catalog.value)
            catalogRequest.response.complete(Reply(catalog(8)))
            browser.catalogRefreshing.first { !it }

            assertEquals(
                listOf(8L),
                assertIs<Load.Ready<List<CatalogThread>>>(browser.catalog.value).value.map { it.key.thread.value },
            )
            assertSame(loadedThread, assertIs<Load.Ready<Thread>>(browser.thread.value).value)
            assertEquals(Route.ThreadPage(key), browser.backStack.value.last())
        }

    @Test
    fun narrowNavigationCancelsAnInitialCatalogLoadWhenTheThreadCoversIt() =
        runTest {
            val fixture = Fixture()
            val browser = fixture.browser(backgroundScope)
            val board = browser.board(FOURCHAN)
            val catalogRequest = fixture.gate(FOURCHAN_CATALOG)
            browser.openBoard(board)
            catalogRequest.entered.await()

            browser.openThread(ThreadKey(FOURCHAN, board.board.id, ThreadId(7)))
            catalogRequest.awaitCancellation()

            assertFalse(browser.catalogRefreshing.value)
            assertNull(browser.catalogRefreshFailure.value, "Hiding a request must not report a network failure")
            assertTrue(browser.preparedCatalogRows.value.isEmpty())
            assertIs<Load.Ready<Thread>>(browser.thread.settled())
        }

    @Test
    fun backCancelsAnInitialThreadLoadBeforeReturningToTheLoadedCatalog() =
        runTest {
            val fixture = Fixture()
            val browser = fixture.browser(backgroundScope)
            val board = browser.board(FOURCHAN)
            browser.openBoard(board)
            val loadedCatalog = assertIs<Load.Ready<List<CatalogThread>>>(browser.catalog.settled())
            val threadRequest = fixture.gate(FOURCHAN_THREAD)
            browser.openThread(ThreadKey(FOURCHAN, board.board.id, ThreadId(7)))
            threadRequest.entered.await()

            assertTrue(browser.back())
            threadRequest.awaitCancellation()

            assertEquals(Route.Catalog(board), browser.backStack.value.last())
            assertSame(loadedCatalog, browser.catalog.value)
            assertFalse(browser.threadRefreshing.value)
            assertNull(browser.threadRefreshFailure.value)
            assertNull(browser.preparedThread.value)
        }

    @Test
    fun aHiddenCatalogRefreshIsCancelledAndBackReusesItsLoadedRowsWithoutAnotherRequest() =
        runTest {
            val fixture = Fixture()
            val browser = fixture.browser(backgroundScope)
            val board = browser.board(FOURCHAN)
            browser.openBoard(board)
            val loaded = assertIs<Load.Ready<List<CatalogThread>>>(browser.catalog.settled())
            val prepared = browser.preparedCatalogRows.value
            val refresh = fixture.gate(FOURCHAN_CATALOG)
            browser.refreshCatalog(board)
            refresh.entered.await()
            assertTrue(browser.catalogRefreshing.value)

            browser.openThread(ThreadKey(FOURCHAN, board.board.id, ThreadId(7)))
            refresh.awaitCancellation()
            browser.thread.settled()
            val requests = fixture.requests(FOURCHAN_CATALOG)

            assertFalse(browser.catalogRefreshing.value)
            assertSame(loaded, browser.catalog.value)
            assertSame(prepared, browser.preparedCatalogRows.value)
            assertNull(browser.catalogRefreshFailure.value)
            assertTrue(browser.back())

            assertSame(loaded, browser.catalog.value)
            assertSame(prepared, browser.preparedCatalogRows.value)
            assertFalse(browser.catalogRefreshing.value, "Returning to retained rows must not restart a refresh")
            assertEquals(requests, fixture.requests(FOURCHAN_CATALOG))
        }

    @Test
    fun backCancelsAThreadRefreshAndReopeningReusesItsPreparedPostsWithoutAnotherRequest() =
        runTest {
            val fixture = Fixture()
            val browser = fixture.browser(backgroundScope)
            val board = browser.board(FOURCHAN)
            browser.openBoard(board)
            browser.catalog.settled()
            val key = ThreadKey(FOURCHAN, board.board.id, ThreadId(7))
            browser.openThread(key)
            val loaded = assertIs<Load.Ready<Thread>>(browser.thread.settled())
            val prepared = browser.preparedThread.value
            val refresh = fixture.gate(FOURCHAN_THREAD)
            browser.retry()
            refresh.entered.await()

            assertTrue(browser.back())
            refresh.awaitCancellation()
            val requests = fixture.requests(FOURCHAN_THREAD)

            assertSame(loaded, browser.thread.value)
            assertSame(prepared, browser.preparedThread.value)
            assertFalse(browser.threadRefreshing.value)
            assertNull(browser.threadRefreshFailure.value)
            browser.openThread(key)

            assertSame(loaded, browser.thread.value)
            assertSame(prepared, browser.preparedThread.value)
            assertFalse(browser.threadRefreshing.value, "Reopening retained posts must not restart the refresh")
            assertEquals(requests, fixture.requests(FOURCHAN_THREAD))
        }

    @Test
    fun resizingAWideReaderToOnePaneCancelsOnlyTheHiddenCatalogRequest() =
        runTest {
            val fixture = Fixture()
            val browser = fixture.browser(backgroundScope)
            val board = browser.board(FOURCHAN)
            val catalogRequest = fixture.gate(FOURCHAN_CATALOG)
            browser.openBoard(board)
            catalogRequest.entered.await()
            browser.setReaderPanesVisible(
                BoardReaderRoute(Route.Catalog(board)),
                wideWindow = true,
                catalogVisible = true,
                threadVisible = false,
            )
            val key = ThreadKey(FOURCHAN, board.board.id, ThreadId(7))
            val threadRequest = fixture.gate(FOURCHAN_THREAD)
            browser.openThread(key)
            threadRequest.entered.await()
            assertFalse(catalogRequest.cancelled.isCompleted, "Both visible panes keep their requests")

            browser.setReaderPanesVisible(
                BoardReaderRoute(Route.Catalog(board), Route.ThreadPage(key)),
                wideWindow = false,
                catalogVisible = false,
                threadVisible = true,
            )
            catalogRequest.awaitCancellation()

            assertFalse(threadRequest.cancelled.isCompleted)
            threadRequest.response.complete(Reply(thread(7, "Visible thread")))
            val visible = assertIs<Load.Ready<Thread>>(browser.thread.settled()).value
            assertEquals("Visible thread", visible.subject)
            assertSame(visible, assertNotNull(browser.preparedThread.value).thread)
            assertNull(browser.catalogRefreshFailure.value)
        }

    @Test
    fun switchingRootDestinationCancelsBothRequestsInAWideReader() =
        runTest {
            val fixture = Fixture()
            val browser = fixture.browser(backgroundScope)
            val board = browser.board(FOURCHAN)
            browser.openBoard(board)
            val loaded = assertIs<Load.Ready<List<CatalogThread>>>(browser.catalog.settled())
            browser.setReaderPanesVisible(
                BoardReaderRoute(Route.Catalog(board)),
                wideWindow = true,
                catalogVisible = true,
                threadVisible = false,
            )
            val catalogRequest = fixture.gate(FOURCHAN_CATALOG)
            browser.refreshCatalog(board)
            catalogRequest.entered.await()
            val threadRequest = fixture.gate(FOURCHAN_THREAD)
            browser.openThread(ThreadKey(FOURCHAN, board.board.id, ThreadId(7)))
            threadRequest.entered.await()

            browser.openTab(Route.Saved)
            catalogRequest.awaitCancellation()
            threadRequest.awaitCancellation()

            assertEquals(listOf(Route.Saved), browser.backStack.value)
            assertSame(loaded, browser.catalog.value)
            assertFalse(browser.catalogRefreshing.value)
            assertFalse(browser.threadRefreshing.value)
            assertNull(browser.catalogRefreshFailure.value)
            assertNull(browser.threadRefreshFailure.value)
        }

    @Test
    fun theBoardPickerReplacesItsCatalogInPlaceAndResetsSortOnlyForAnotherBoard() =
        runTest {
            val fixture = Fixture()
            val browser = fixture.browser(backgroundScope)
            val first = browser.board(FOURCHAN)
            val second = browser.board(BBWCHAN)
            browser.openTab(Route.Boards)
            browser.openBoard(first)
            browser.catalog.settled()
            browser.cycleCatalogSort()

            browser.replaceBoard(first)

            assertEquals(listOf(Route.Boards, Route.Catalog(first)), browser.backStack.value)
            assertEquals(CatalogSort.CREATION_DATE, browser.catalogSort.value, "Keeping the board retains its sort")
            val request = fixture.gate(BBWCHAN_CATALOG)
            browser.replaceBoard(second)
            request.entered.await()

            assertEquals(listOf(Route.Boards, Route.Catalog(second)), browser.backStack.value)
            assertEquals(CatalogSort.BUMP_ORDER, browser.catalogSort.value, "Another board starts in bump order")
            request.response.complete(Reply("""[{"threadId":19,"subject":"Other site"}]"""))
            browser.catalog.settled()
            assertTrue(browser.back())
            assertEquals(listOf(Route.Boards), browser.backStack.value)
        }

    @Test
    fun theBoardPickerDropsTheAdjacentThreadAndKeepsTheOriginalRootDestination() =
        runTest {
            val fixture = Fixture()
            val browser = fixture.browser(backgroundScope)
            val first = browser.board(FOURCHAN)
            val second = browser.board(BBWCHAN)
            browser.openTab(Route.Boards)
            browser.openBoard(first)
            browser.catalog.settled()
            browser.openThread(ThreadKey(FOURCHAN, first.board.id, ThreadId(7)))
            browser.thread.settled()
            val request = fixture.gate(BBWCHAN_CATALOG)

            browser.replaceBoard(second)
            request.entered.await()

            assertEquals(listOf(Route.Boards, Route.Catalog(second)), browser.backStack.value)
            assertFalse(browser.backStack.value.any { it is Route.ThreadPage })
            assertIs<Load.Loading>(browser.catalog.value)
            request.response.complete(Reply("""[{"threadId":19,"subject":"Other site"}]"""))
            val loaded = assertIs<Load.Ready<List<CatalogThread>>>(browser.catalog.settled()).value
            assertEquals(listOf(BBWCHAN), loaded.map { it.key.provider })
        }

    @Test
    fun boardWebUrlsUseTheProvidersPublicSiteAndUnknownSitesHaveNoAction() =
        runTest {
            val browser = Fixture().browser(backgroundScope)

            assertEquals("https://boards.4chan.org/x/", browser.boardWebUrl(browser.board(FOURCHAN)))
            assertEquals("https://bbw-chan.link/x/", browser.boardWebUrl(browser.board(BBWCHAN)))
            assertNull(browser.boardWebUrl(SiteBoard(ProviderId("unknown"), "Unknown", Board(BoardId("x"), "Unknown"))))
        }

    private suspend fun Browser.board(provider: ProviderId): SiteBoard {
        firstRun.state.first { it.initialized && !it.needed }
        return assertIs<Load.Ready<List<SiteBoard>>>(boards.settled()).value.first {
            it.provider == provider && it.board.id == BoardId("x")
        }
    }

    private suspend fun <T> StateFlow<Load<T>>.settled(): Load<T> = first { it !is Load.Loading }

    private suspend fun RequestGate.awaitCancellation() {
        // Ktor delivers cancellation on its own dispatcher, so use a real timeout rather than
        // allowing the test scheduler to advance past an external coroutine immediately.
        withContext(Dispatchers.Default) { withTimeout(5_000) { cancelled.await() } }
    }

    private class Fixture {
        private val gates = MutableStateFlow<Map<String, RequestGate>>(emptyMap())
        private val requestCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

        fun gate(path: String): RequestGate = RequestGate().also { gate -> gates.update { it + (path to gate) } }

        fun requests(path: String): Int = requestCounts.value[path] ?: 0

        fun browser(scope: CoroutineScope): Browser {
            val engine =
                MockEngine { request ->
                    val path = "${request.url.host}${request.url.encodedPath}"
                    requestCounts.update { counts -> counts + (path to ((counts[path] ?: 0) + 1)) }
                    val reply =
                        gates.value[path]?.let { gate ->
                            gate.entered.complete(Unit)
                            try {
                                gate.response.await()
                            } catch (cancelled: CancellationException) {
                                gate.cancelled.complete(Unit)
                                throw cancelled
                            }
                        } ?: Reply(ROUTES[path], if (path in ROUTES) HttpStatusCode.OK else HttpStatusCode.NotFound)
                    respond(
                        reply.body.orEmpty(),
                        reply.status,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            return Browser(
                orbinProviders(iosHttpClient(engine)),
                FakeBookmarks(),
                FakeHistory(),
                FakeBoardPreferences().apply { subscribed.value = mapOf(FOURCHAN to setOf(BoardId("g"))) },
                FakeSettings().apply { settings.value = settings.value.copy(onboardingCompleted = true) },
                scope,
            ).also { it.selectFeedMode(HomeFeedMode.FOLLOWING) }
        }
    }

    private class RequestGate {
        val entered = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Reply>()
        val cancelled = CompletableDeferred<Unit>()
    }

    private data class Reply(
        val body: String? = null,
        val status: HttpStatusCode = HttpStatusCode.OK,
    )

    private companion object {
        val FOURCHAN = ProviderId("fourchan")
        val BBWCHAN = ProviderId("bbwchan")
        const val FOURCHAN_CATALOG = "a.4cdn.org/x/catalog.json"
        const val BBWCHAN_CATALOG = "bbw-chan.link/x/catalog.json"
        const val FOURCHAN_THREAD = "a.4cdn.org/x/thread/7.json"
        val ROUTES =
            mapOf(
                "a.4cdn.org/boards.json" to
                    """{"boards":[{"board":"g","title":"Technology","ws_board":1},{"board":"x","title":"Paranormal","ws_board":1}]}""",
                "a.4cdn.org/g/catalog.json" to "[]",
                FOURCHAN_CATALOG to catalog(7),
                FOURCHAN_THREAD to thread(7, "Original thread"),
                "bbw-chan.link/boards.js" to
                    """{"status":"ok","data":{"boards":[{"boardUri":"x","boardName":"Other board"}]}}""",
            )

        fun catalog(id: Long): String = """[{"page":1,"threads":[{"no":$id,"sub":"Catalog thread","time":1}]}]"""

        fun thread(
            id: Long,
            subject: String,
        ): String = """{"posts":[{"no":$id,"sub":"$subject","time":1}]}"""
    }
}
