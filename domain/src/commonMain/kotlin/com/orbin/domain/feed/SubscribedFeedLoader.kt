package com.orbin.domain.feed

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.core.model.matchesFilterTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** One followed board in the feed: its threads, its thread-count override, and whether it failed. */
data class BoardFeed(
    val board: Board,
    val threads: List<CatalogThread>,
    /** This board's thread-count override, if any; null means it follows the global default. */
    val limitOverride: FeedThreadLimit? = null,
    val failed: Boolean = false,
)

/**
 * How the subscribed feed is built, on Android and iOS alike: which followed boards it covers,
 * each board's catalog cut to its thread limit and passed through the permanent filter, a few
 * boards at a time, and a failed board kept apart from the others rather than failing the feed.
 */
class SubscribedFeedLoader(
    private val maxConcurrentBoards: Int = MAX_CONCURRENT_BOARD_LOADS,
) {
    /**
     * The followed boards the feed covers, in board-id order: those in [subscribed], less NSFW
     * ones when [hideNsfw] is on and any the permanent filter catches by title.
     */
    fun feedBoards(
        boards: List<Board>,
        subscribed: Set<BoardId>,
        hideNsfw: Boolean,
    ): List<Board> =
        boards
            .filter { it.id in subscribed }
            .filterNot { hideNsfw && it.isNsfw }
            .filterNot { it.isPermanentlyFiltered() }
            .sortedBy { it.id.value }

    /**
     * Loads every board in [boards] with [catalog], at most [maxConcurrentBoards] at once, keeping
     * the input order. A board whose catalog throws comes back empty with [BoardFeed.failed] set;
     * [onFailure] hears about it for logging. Cancellation always propagates.
     */
    suspend fun load(
        boards: List<Board>,
        limits: Map<BoardId, FeedThreadLimit>,
        onFailure: (BoardId, Exception) -> Unit = { _, _ -> },
        catalog: suspend (BoardId) -> List<CatalogThread>,
    ): List<BoardFeed> {
        val gate = Semaphore(maxConcurrentBoards)
        return coroutineScope {
            boards
                .map { board ->
                    async { gate.withPermit { loadBoard(board, limits[board.id], onFailure, catalog) } }
                }.awaitAll()
        }
    }

    private suspend fun loadBoard(
        board: Board,
        limit: FeedThreadLimit?,
        onFailure: (BoardId, Exception) -> Unit,
        catalog: suspend (BoardId) -> List<CatalogThread>,
    ): BoardFeed =
        try {
            val threads = catalog(board.id)
            val scoped = limit?.count?.let(threads::take) ?: threads
            BoardFeed(board, scoped.filterNot { it.matchesFilterTokens(emptySet()) }, limit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            onFailure(board.id, error)
            BoardFeed(board, emptyList(), limit, failed = true)
        }

    companion object {
        const val MAX_CONCURRENT_BOARD_LOADS = 4

        /**
         * [current] with each failed board's threads taken from [cached], so a board that failed
         * this time keeps what it showed last time instead of going blank. Still marked failed.
         */
        fun withCachedFailures(
            current: List<BoardFeed>,
            cached: List<BoardFeed>?,
        ): List<BoardFeed> {
            if (cached == null || current.none { it.failed }) return current
            val cachedByBoard = cached.associateBy { it.board.id }
            return current.map { feed ->
                val previous = cachedByBoard[feed.board.id]
                if (feed.failed && previous != null) previous.copy(failed = true) else feed
            }
        }
    }
}
