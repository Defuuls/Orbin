package com.orbin.feature.home

import co.touchlab.kermit.Logger
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.HomeFeedMode
import com.orbin.core.model.ProviderId
import com.orbin.domain.feed.BoardFeed
import com.orbin.domain.feed.SubscribedFeedLoader
import com.orbin.provider.api.ImageBoardProvider
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal data class FeedInputs(
    val providerId: String,
    val boards: List<Board>,
    val limitOverrides: Map<BoardId, FeedThreadLimit>,
    val settings: FeedLoadSettings,
    val refreshCount: Int,
    val mode: HomeFeedMode,
    val providerFilter: String?,
    val canLoadMore: Boolean,
)

/** A paged discovery shares catalog bytes with Following but keeps presentation caches separate. */
internal class HomeFeedCatalogLoader(
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val loader = SubscribedFeedLoader()
    private val gate = Semaphore(SubscribedFeedLoader.MAX_CONCURRENT_BOARD_LOADS)
    private val pages = mutableMapOf<Pair<String, HomeFeedMode>, CachedFeed>()
    private val boards = mutableMapOf<Pair<ProviderId, BoardId>, CachedBoard>()
    private var settings: FeedLoadSettings? = null

    fun prepare(current: FeedLoadSettings): Boolean {
        if (settings == current) return false
        pages.clear()
        boards.clear()
        settings = current
        return true
    }

    /** Publishes each catalog as it finishes; one slow source cannot hold every other page blank. */
    fun load(
        provider: ImageBoardProvider,
        inputs: FeedInputs,
    ): Flow<SubscribedFeedUiState> =
        channelFlow {
            val key = inputs.providerId to inputs.mode
            val cached = pages[key]?.takeIf { it.inputs.settings == inputs.settings }
            if (cached?.inputs == inputs) {
                send(cached.state.forDisplay())
                return@channelFlow
            }
            val results = reusableBoards(provider, inputs)
            send(initialState(inputs, results, cached?.state).forDisplay())
            val mutex = Mutex()
            coroutineScope {
                inputs.boards
                    .mapIndexedNotNull { index, board ->
                        if (index in results) return@mapIndexedNotNull null
                        async {
                            val loaded = loadBoard(provider, board, inputs)
                            mutex.withLock {
                                if (settings != inputs.settings) return@withLock
                                val boardKey = provider.metadata.id to board.id
                                val previous = boards[boardKey]?.takeIf { it.settings == inputs.settings }?.feed
                                val usable =
                                    if (loaded.failed && previous != null) {
                                        loaded.copy(
                                            threads =
                                                previous.threads.take(
                                                    inputs.limitOverrides[board.id]?.count ?: previous.threads.size,
                                                ),
                                        )
                                    } else {
                                        loaded
                                    }
                                results[index] = usable
                                // A failed board is also complete for this generation. Paging
                                // must not repeatedly retry it; an explicit refresh changes the key.
                                boards[boardKey] =
                                    CachedBoard(
                                        usable,
                                        inputs.refreshCount,
                                        inputs.limitOverrides[board.id],
                                        inputs.settings,
                                    )
                                send(snapshot(inputs, results, cached?.state).forDisplay())
                            }
                        }
                    }.awaitAll()
            }
            val final = snapshot(inputs, results, cached?.state)
            if (settings == inputs.settings &&
                (final.failedBoards.size < final.boards.size || final.boards.isEmpty())
            ) {
                pages[key] = CachedFeed(inputs, final)
            }
        }

    private fun reusableBoards(
        provider: ImageBoardProvider,
        inputs: FeedInputs,
    ): MutableMap<Int, BoardFeed> =
        inputs.boards
            .mapIndexedNotNull { index, board ->
                val cached = boards[provider.metadata.id to board.id]
                cached
                    ?.takeIf {
                        it.refreshCount == inputs.refreshCount &&
                            it.limit == inputs.limitOverrides[board.id] &&
                            it.settings == inputs.settings
                    }?.let { index to it.feed.copy(board = board) }
            }.toMap()
            .toMutableMap()

    private suspend fun loadBoard(
        provider: ImageBoardProvider,
        board: Board,
        inputs: FeedInputs,
    ): BoardFeed =
        gate.withPermit {
            // Provider decorators resume after network suspension. Keep their cover scans and
            // the loader's permanent-content filters off the controller's Main dispatcher too.
            withContext(workDispatcher) {
                loader
                    .load(listOf(board), inputs.limitOverrides, onFailure = { _, error ->
                        Logger.w(
                            tag = "HomeFeedCatalogLoader",
                        ) { "Failed to load catalog (${error::class.simpleName})" }
                    }) { id -> provider.getCatalog(CatalogRequest(provider.metadata.id, id)) }
                    .single()
            }
        }

    private fun initialState(
        inputs: FeedInputs,
        results: Map<Int, BoardFeed>,
        cached: SubscribedFeedUiState.Success?,
    ): SubscribedFeedUiState {
        if (results.isNotEmpty() || inputs.boards.isEmpty()) return snapshot(inputs, results, cached)
        val wanted = inputs.boards.associateBy { it.id }
        val retained =
            cached?.boards.orEmpty().mapNotNull { old ->
                wanted[old.board.id]?.let { current ->
                    old.copy(board = current).withLimit(inputs.limitOverrides[current.id])
                }
            }
        return if (retained.isEmpty()) {
            SubscribedFeedUiState.Loading
        } else {
            cached!!.copy(
                boards = retained.toImmutableList(),
                isLoadingMore = true,
                canLoadMore = inputs.canLoadMore,
                mode = inputs.mode,
                providerFilter = inputs.providerFilter,
            )
        }
    }

    private fun snapshot(
        inputs: FeedInputs,
        results: Map<Int, BoardFeed>,
        cached: SubscribedFeedUiState.Success?,
    ): SubscribedFeedUiState.Success {
        val previous = cached?.boards.orEmpty().associateBy { it.board.id }
        val displayed =
            inputs.boards.mapIndexedNotNull { index, board ->
                val feed = results[index]
                val prior = previous[board.id]
                when {
                    // A completed refresh replaces only its own board. Other requested boards
                    // keep their cached rows until they settle, without reviving removed boards.
                    feed == null -> prior?.copy(board = board)?.withLimit(inputs.limitOverrides[board.id])
                    feed.failed && prior != null ->
                        prior.copy(board = board, failed = true).withLimit(inputs.limitOverrides[board.id])
                    else ->
                        SubscribedBoardFeed(
                            board,
                            feed.threads.toImmutableList(),
                            feed.limitOverride,
                            inputs.providerId,
                            feed.failed,
                        )
                }
            }
        return SubscribedFeedUiState.Success(
            boards = displayed.toImmutableList(),
            failedBoards = displayed.filter { it.failed }.map { it.board.id }.toImmutableList(),
            loadedAtMillis = System.currentTimeMillis(),
            stale = displayed.any { it.failed },
            isLoadingMore = results.size < inputs.boards.size,
            canLoadMore = inputs.canLoadMore,
            mode = inputs.mode,
            providerFilter = inputs.providerFilter,
            errorMessage = "Some boards couldn't be loaded. Try again.".takeIf { displayed.any { it.failed } },
        )
    }
}

private fun SubscribedBoardFeed.withLimit(limit: FeedThreadLimit?): SubscribedBoardFeed =
    copy(threads = (limit?.count?.let(threads::take) ?: threads).toImmutableList(), threadLimitOverride = limit)

/** An outage is retryable, not evidence that the reader has no followed boards or videos. */
private fun SubscribedFeedUiState.forDisplay(): SubscribedFeedUiState {
    if (this !is SubscribedFeedUiState.Success) return this
    val waitingForCatalogs = isLoadingMore
    val entirePageFailed = failedBoards.isNotEmpty() && failedBoards.size == boards.size
    val noCachedThreads = boards.all { it.threads.isEmpty() }
    return if (!waitingForCatalogs && entirePageFailed && noCachedThreads) {
        SubscribedFeedUiState.Error("Couldn't load videos. Try again.")
    } else {
        this
    }
}

private data class CachedBoard(
    val feed: BoardFeed,
    val refreshCount: Int,
    val limit: FeedThreadLimit?,
    val settings: FeedLoadSettings,
)

private data class CachedFeed(
    val inputs: FeedInputs,
    val state: SubscribedFeedUiState.Success,
)
