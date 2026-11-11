package com.orbin.ios

import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.HomeFeedMode
import com.orbin.core.model.ProviderId
import com.orbin.graph.orbinProviders
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Actual Browser publication waits for worker filtering and prepared rows from the same snapshot. */
class BrowserCatalogPreparationTest {
    @Test
    fun aCatalogIsPublishedOnlyAfterWorkerFilteringAndRowPreparationComplete() =
        runTest {
            val readerWorker = QueuedReaderDispatcher()
            val engine =
                MockEngine { request ->
                    val json =
                        when (request.url.encodedPath) {
                            "/boards.json" -> BOARDS
                            "/g/catalog.json" -> "[]"
                            "/x/catalog.json" -> CATALOG
                            else -> null
                        }
                    respond(
                        json.orEmpty(),
                        if (json == null) HttpStatusCode.NotFound else HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val browser =
                Browser(
                    orbinProviders(iosHttpClient(engine)).filter { it.metadata.id == FOURCHAN },
                    FakeBookmarks(),
                    FakeHistory(),
                    FakeBoardPreferences().apply { subscribed.value = mapOf(FOURCHAN to setOf(BoardId("g"))) },
                    FakeSettings().apply { settings.value = settings.value.copy(onboardingCompleted = true) },
                    backgroundScope,
                    readerDispatcher = readerWorker,
                ).also { it.selectFeedMode(HomeFeedMode.FOLLOWING) }
            browser.firstRun.state.first { it.initialized && !it.needed }
            val board =
                assertIs<Load.Ready<List<SiteBoard>>>(browser.boards.first { it is Load.Ready })
                    .value
                    .first { it.board.id == BoardId("x") }
            browser.openBoard(board)
            val filterWork = readerWorker.nextTask()

            assertIs<Load.Loading>(browser.catalog.value)
            assertTrue(browser.preparedCatalogRows.value.isEmpty(), "Unfiltered data must never reach the reader")
            filterWork.run()
            val prepareWork = readerWorker.nextTask()

            assertIs<Load.Loading>(browser.catalog.value)
            assertTrue(browser.preparedCatalogRows.value.isEmpty(), "Rows and filtered data must publish together")
            prepareWork.run()
            val loaded =
                assertIs<Load.Ready<List<CatalogThread>>>(browser.catalog.first { it is Load.Ready }).value

            assertEquals(listOf(7L), loaded.map { it.key.thread.value }, "The blocked thread must be excluded")
            assertEquals(loaded.map { it.key }.toSet(), browser.preparedCatalogRows.value.keys)
            assertEquals(
                "Allowed thread",
                browser.preparedCatalogRows.value.values
                    .single()
                    .subject,
            )
        }

    private class QueuedReaderDispatcher : CoroutineDispatcher() {
        private val tasks = Channel<Runnable>(Channel.UNLIMITED)

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            check(tasks.trySend(block).isSuccess)
        }

        suspend fun nextTask(): Runnable = tasks.receive()
    }

    private companion object {
        val FOURCHAN = ProviderId("fourchan")
        const val BOARDS =
            """{"boards":[{"board":"g","title":"Technology","ws_board":1},{"board":"x","title":"Paranormal","ws_board":1}]}"""
        const val CATALOG =
            """[{"page":1,"threads":[{"no":7,"sub":"Allowed thread","time":1},{"no":8,"sub":"gore video","time":2}]}]"""
    }
}
