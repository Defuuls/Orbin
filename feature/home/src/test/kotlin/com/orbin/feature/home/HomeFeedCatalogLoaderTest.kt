package com.orbin.feature.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.HomeFeedMode
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedCatalogLoaderTest {
    private val firstBoard = Board(BoardId("a"), "Animation")
    private val delayedBoard = Board(BoardId("g"), "Technology")
    private val settings = FeedLoadSettings(hideNsfwBoards = false, coverViolentMedia = true)

    @Test
    fun `a completed board refresh does not discard another board's cached rows`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var refreshing = false
            val provider =
                provider { request ->
                    if (refreshing && request.board == delayedBoard.id) release.await()
                    listOf(thread(request, if (refreshing) 2L else 1L))
                }
            val loader = HomeFeedCatalogLoader(StandardTestDispatcher(testScheduler))
            val inputs = inputs(listOf(firstBoard, delayedBoard))
            loader.prepare(settings)
            loader.load(provider, inputs).toList()

            refreshing = true
            loader.load(provider, inputs.copy(refreshCount = 1)).test {
                runCurrent()
                val retained = awaitItem() as SubscribedFeedUiState.Success
                assertThat(
                    retained.boards.map {
                        it.threads
                            .single()
                            .key.thread.value
                    },
                ).containsExactly(1L, 1L).inOrder()
                assertThat(retained.isLoadingMore).isTrue()

                val partial = awaitItem() as SubscribedFeedUiState.Success
                assertThat(partial.boards.map { it.board.id }).containsExactly(firstBoard.id, delayedBoard.id).inOrder()
                assertThat(
                    partial.boards.map {
                        it.threads
                            .single()
                            .key.thread.value
                    },
                ).containsExactly(2L, 1L).inOrder()
                assertThat(partial.isLoadingMore).isTrue()
                expectNoEvents()

                release.complete(Unit)
                val ready = awaitItem() as SubscribedFeedUiState.Success
                assertThat(
                    ready.boards.map {
                        it.threads
                            .single()
                            .key.thread.value
                    },
                ).containsExactly(2L, 2L).inOrder()
                assertThat(ready.isLoadingMore).isFalse()
                assertThat(ready.stale).isFalse()
                assertThat(ready.failedBoards).isEmpty()
                awaitComplete()
            }
        }

    @Test
    fun `refresh keeps only requested cached boards when a pending catalog fails`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var refreshing = false
            val provider =
                provider { request ->
                    if (refreshing && request.board == delayedBoard.id) {
                        release.await()
                        throw ProviderException.NotFound("Catalog unavailable")
                    }
                    listOf(thread(request, if (refreshing) 2L else 1L))
                }
            val loader = HomeFeedCatalogLoader(StandardTestDispatcher(testScheduler))
            val inputs = inputs(listOf(firstBoard, delayedBoard, Board(BoardId("p"), "Photography")))
            loader.prepare(settings)
            loader.load(provider, inputs).toList()

            refreshing = true
            val selected = inputs.copy(boards = listOf(firstBoard, delayedBoard), refreshCount = 1)
            loader.load(provider, selected).test {
                runCurrent()
                val retained = awaitItem() as SubscribedFeedUiState.Success
                assertThat(retained.boards.map { it.board.id })
                    .containsExactly(firstBoard.id, delayedBoard.id)
                    .inOrder()

                val partial = awaitItem() as SubscribedFeedUiState.Success
                assertThat(partial.boards.map { it.board.id }).containsExactly(firstBoard.id, delayedBoard.id).inOrder()
                assertThat(partial.isLoadingMore).isTrue()
                expectNoEvents()

                release.complete(Unit)
                val ready = awaitItem() as SubscribedFeedUiState.Success
                assertThat(ready.boards.map { it.board.id }).containsExactly(firstBoard.id, delayedBoard.id).inOrder()
                assertThat(
                    ready.boards.map {
                        it.threads
                            .single()
                            .key.thread.value
                    },
                ).containsExactly(2L, 1L).inOrder()
                assertThat(ready.failedBoards).containsExactly(delayedBoard.id)
                assertThat(ready.boards.last().failed).isTrue()
                assertThat(ready.isLoadingMore).isFalse()
                assertThat(ready.stale).isTrue()
                assertThat(ready.errorMessage).isNotNull()
                awaitComplete()
            }
        }

    private fun inputs(boards: List<Board>) =
        FeedInputs(
            providerId = "fourchan",
            boards = boards,
            limitOverrides = emptyMap(),
            settings = settings,
            refreshCount = 0,
            mode = HomeFeedMode.FOLLOWING,
            providerFilter = null,
            canLoadMore = false,
        )

    private fun provider(catalog: suspend (CatalogRequest) -> List<CatalogThread>) =
        object : ImageBoardProvider {
            override val metadata = ProviderMetadata(ProviderId("fourchan"), "4chan", "https://example.org")
            override val capabilities = ProviderCapabilities()

            override suspend fun getBoards(): List<Board> = emptyList()

            override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> = catalog(request)

            override suspend fun getThread(
                board: BoardId,
                thread: ThreadId,
            ): Thread = throw ProviderException.NotFound("Unused")
        }

    private fun thread(
        request: CatalogRequest,
        id: Long,
    ) = CatalogThread(
        key = ThreadKey(request.provider, request.board, ThreadId(id)),
        originalPost =
            Post(
                id = PostId(id),
                board = request.board,
                threadId = ThreadId(id),
                isOriginalPost = true,
            ),
        stats = ThreadStats(),
    )
}
