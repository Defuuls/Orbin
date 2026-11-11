package com.orbin.feature.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.core.reader.PreparedCatalogThread
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBookmarkRepository
import com.orbin.core.testing.repository.FakeHistoryRepository
import com.orbin.core.testing.repository.FakeProviderRegistry
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.data.repository.CatalogRepositoryImpl
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderMetadata
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Uses the real repository Pager and UI presenter, including refresh generations and cached replay. */
@OptIn(ExperimentalCoroutinesApi::class)
class BoardPagingLifecycleTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun hidingARefreshCancelsRemoteWorkAndBackReplaysLoadedRowsWithoutAnotherRequest() =
        runTest {
            val provider = Provider()
            val viewModel = viewModel(provider)
            try {
                val presenter = presenter()
                val collection = collect(backgroundScope, viewModel, presenter)
                presenter.ready(1)
                val loaded = presenter.snapshot().items.map { it.thread.key }
                val refresh = provider.gate()
                presenter.refresh()
                refresh.entered.await()

                viewModel.setCatalogVisible(false)
                refresh.cancelled.await()
                presenter.ready(1)
                assertThat(presenter.snapshot().items.map { it.thread.key }).containsExactlyElementsIn(loaded).inOrder()
                collection.cancelAndJoin()

                viewModel.setCatalogVisible(true)
                val reopened = presenter()
                collect(backgroundScope, viewModel, reopened)
                reopened.ready(1)
                assertThat(reopened.snapshot().items.map { it.thread.key }).containsExactlyElementsIn(loaded).inOrder()
                assertThat(provider.requests.get()).isEqualTo(2)
            } finally {
                viewModel.viewModelScope.cancel()
            }
        }

    @Test
    fun aFailedRefreshKeepsLoadedRowsWhenTheCatalogIsReopened() =
        runTest {
            val provider = Provider()
            val viewModel = viewModel(provider)
            try {
                val presenter = presenter()
                val collection = collect(backgroundScope, viewModel, presenter)
                presenter.ready(1)
                val loaded = presenter.snapshot().items.map { it.thread.key }
                val refresh = provider.gate()
                presenter.refresh()
                refresh.entered.await()
                refresh.response.completeExceptionally(IllegalStateException("Refresh failed"))
                val failure = viewModel.catalogRefreshFailure.filterNotNull().first()
                assertThat(failure).hasMessageThat().isEqualTo("Refresh failed")
                presenter.ready(1)
                assertThat(presenter.snapshot().items.map { it.thread.key }).containsExactlyElementsIn(loaded).inOrder()

                viewModel.setCatalogVisible(false)
                collection.cancelAndJoin()
                viewModel.setCatalogVisible(true)
                val reopened = presenter()
                collect(backgroundScope, viewModel, reopened)
                reopened.ready(1)
                assertThat(viewModel.catalogRefreshFailure.value).isSameInstanceAs(failure)
                assertThat(reopened.snapshot().items.map { it.thread.key }).containsExactlyElementsIn(loaded).inOrder()
                assertThat(provider.requests.get()).isEqualTo(2)

                val retry = provider.gate()
                // Retained errors retry with refresh: their populated generation is already Ready.
                reopened.refresh()
                retry.entered.await()
                assertThat(viewModel.catalogRefreshFailure.value).isNull()
                retry.response.complete(provider.rows(3))
                reopened.ready(3)
                assertThat(provider.requests.get()).isEqualTo(3)
            } finally {
                viewModel.viewModelScope.cancel()
            }
        }

    @Test
    fun anEmptyCatalogKeepsItsRefreshFailureUntilExplicitRetry() =
        runTest {
            val provider = Provider(itemCount = 0)
            val viewModel = viewModel(provider)
            try {
                val presenter = presenter()
                collect(backgroundScope, viewModel, presenter)
                presenter.loadStateFlow.filterNotNull().first { it.refresh is LoadState.NotLoading }
                assertThat(presenter.snapshot().items).isEmpty()
                val refresh = provider.gate()
                presenter.refresh()
                refresh.entered.await()
                refresh.response.completeExceptionally(IllegalStateException("Empty catalog refresh failed"))

                val failure = viewModel.catalogRefreshFailure.filterNotNull().first()
                assertThat(failure).hasMessageThat().isEqualTo("Empty catalog refresh failed")
                val retained = presenter.loadStateFlow.filterNotNull().first { it.refresh is LoadState.NotLoading }
                assertThat(
                    boardCatalogLoadState(presenter.size, retained.refresh).content,
                ).isEqualTo(BoardCatalogContent.EMPTY)
                assertThat(presenter.snapshot().items).isEmpty()
                assertThat(viewModel.catalogRefreshFailure.value).isSameInstanceAs(failure)

                val retry = provider.gate()
                presenter.refresh()
                retry.entered.await()
                assertThat(viewModel.catalogRefreshFailure.value).isNull()
                retry.response.complete(emptyList())
                presenter.loadStateFlow.filterNotNull().first { it.refresh is LoadState.NotLoading }
                assertThat(presenter.snapshot().items).isEmpty()
                assertThat(provider.requests.get()).isEqualTo(3)
            } finally {
                viewModel.viewModelScope.cancel()
            }
        }

    @Test
    fun anInitialLoadWaitsWhileHiddenAndResumesOnlyWhenThePaneReturns() =
        runTest {
            val provider = Provider()
            val initial = provider.gate()
            val viewModel = viewModel(provider)
            try {
                val presenter = presenter()
                collect(backgroundScope, viewModel, presenter)
                initial.entered.await()
                viewModel.setCatalogVisible(false)
                initial.cancelled.await()
                val resumed = provider.gate()
                runCurrent()
                assertThat(provider.requests.get()).isEqualTo(1)
                assertThat(presenter.snapshot().items).isEmpty()

                viewModel.setCatalogVisible(true)
                resumed.entered.await()
                resumed.response.complete(provider.rows(2))
                presenter.ready(2)
                assertThat(provider.requests.get()).isEqualTo(2)
            } finally {
                viewModel.viewModelScope.cancel()
            }
        }

    @Test
    fun aVisibleCatalogCompletesItsRefreshWithoutCancelingTheRequest() =
        runTest {
            val provider = Provider()
            val viewModel = viewModel(provider)
            try {
                val presenter = presenter()
                collect(backgroundScope, viewModel, presenter)
                presenter.ready(1)
                val refresh = provider.gate()
                presenter.refresh()
                refresh.entered.await()
                // A two-pane host keeps its catalog mounted and STARTED while its detail changes.
                viewModel.setCatalogVisible(true)
                runCurrent()
                assertThat(refresh.cancelled.isCompleted).isFalse()
                refresh.response.complete(provider.rows(2))
                presenter.ready(2)
                assertThat(provider.requests.get()).isEqualTo(2)
            } finally {
                viewModel.viewModelScope.cancel()
            }
        }

    @Test
    fun clearingTheOwnerCancelsTheProviderWithoutRecoveringItsCanceledGeneration() =
        runTest {
            val provider = Provider()
            val request = provider.gate()
            val viewModel = viewModel(provider)
            val presenter = presenter()
            collect(backgroundScope, viewModel, presenter)
            request.entered.await()
            viewModel.viewModelScope.cancel()
            request.cancelled.await()
            runCurrent()
            assertThat(presenter.snapshot().items).isEmpty()
            assertThat(provider.requests.get()).isEqualTo(1)
        }

    @Test
    fun refreshingAtTheTopKeepsTheTopOfA500ItemInitialPage() =
        runTest {
            val provider = Provider(itemCount = 1_000)
            val viewModel = viewModel(provider)
            try {
                val presenter = presenter()
                collect(backgroundScope, viewModel, presenter)
                presenter.ready(1)
                assertThat(presenter.snapshot().items).hasSize(500)
                assertThat(
                    presenter[0]
                        ?.thread
                        ?.key
                        ?.thread
                        ?.value,
                ).isEqualTo(1L)
                runCurrent()

                presenter.refresh()
                presenter.ready(2)

                assertThat(presenter.snapshot().items).hasSize(500)
                assertThat(
                    presenter
                        .snapshot()
                        .items
                        .first()
                        .thread.key.thread.value,
                ).isEqualTo(1L)
                assertThat(provider.requests.get()).isEqualTo(2)
            } finally {
                viewModel.viewModelScope.cancel()
            }
        }

    @Test
    fun actualRepositoryAndViewModelFilteringWalkCommentsOnAWorkerBeforePublication() =
        runTest {
            val walks = ConcurrentLinkedQueue<String>()
            val text = persistentListOf<PostNode>(PostNode.Text("A normal comment"))
            val observed =
                object : ImmutableList<PostNode> by text {
                    override fun iterator(): Iterator<PostNode> {
                        walks.add(
                            java.lang.Thread
                                .currentThread()
                                .name,
                        )
                        return text.iterator()
                    }
                }
            val provider = Provider(itemCount = 1, comment = PostComment("", observed))
            val viewModel = viewModel(provider)
            try {
                val presenter = presenter()
                collect(backgroundScope, viewModel, presenter)
                presenter.ready(1)
                assertThat(walks).isNotEmpty()
                assertThat(walks.filterNot { it.startsWith("DefaultDispatcher-worker") }).isEmpty()
                assertThat(
                    presenter
                        .snapshot()
                        .items
                        .single()
                        .row.excerpt,
                ).isEqualTo("A normal comment")
            } finally {
                viewModel.viewModelScope.cancel()
            }
        }

    private fun presenter() =
        object : PagingDataPresenter<PreparedCatalogThread>(mainDispatcher.testDispatcher) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<PreparedCatalogThread>) = Unit
        }

    private fun collect(
        scope: CoroutineScope,
        viewModel: BoardViewModel,
        presenter: PagingDataPresenter<PreparedCatalogThread>,
    ) = scope.launch { viewModel.preparedCatalog.collectLatest(presenter::collectFrom) }

    private suspend fun PagingDataPresenter<PreparedCatalogThread>.ready(revision: Int) {
        loadStateFlow.filterNotNull().first {
            it.refresh is LoadState.NotLoading &&
                snapshot()
                    .items
                    .firstOrNull()
                    ?.row
                    ?.subject == "Revision $revision"
        }
    }

    private fun viewModel(provider: ImageBoardProvider) =
        BoardViewModel(
            SavedStateHandle(mapOf("provider" to "test", "board" to "g")),
            CatalogRepositoryImpl(FakeProviderRegistry(provider)),
            FakeBookmarkRepository(),
            FakeHistoryRepository(),
            FakeSettingsRepository(),
        )

    private class Gate {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val response = CompletableDeferred<List<CatalogThread>>()
    }

    private class Provider(
        private val itemCount: Int = 3,
        private val comment: PostComment = PostComment.Empty,
    ) : ImageBoardProvider {
        override val metadata = ProviderMetadata(ProviderId("test"), "Test", "https://example.invalid")
        override val capabilities = ProviderCapabilities()
        val requests = AtomicInteger()
        private val next = AtomicReference<Gate?>()

        fun gate(): Gate = Gate().also(next::set)

        fun rows(revision: Int) =
            (1..itemCount).map { number ->
                val key = ThreadKey(metadata.id, BoardId("g"), ThreadId(number.toLong()))
                CatalogThread(
                    key,
                    Post(
                        PostId(number.toLong()),
                        key.board,
                        key.thread,
                        isOriginalPost = true,
                        subject = "Revision $revision",
                        comment = comment,
                    ),
                    ThreadStats(),
                )
            }

        override suspend fun getBoards(): List<Board> = emptyList()

        override suspend fun getThread(
            board: BoardId,
            thread: ThreadId,
        ): Thread = error("Unused")

        override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> {
            val revision = requests.incrementAndGet()
            val gate = next.getAndSet(null) ?: return rows(revision)
            gate.entered.complete(Unit)
            try {
                return gate.response.await()
            } catch (cancelled: CancellationException) {
                gate.cancelled.complete(Unit)
                throw cancelled
            }
        }
    }
}
