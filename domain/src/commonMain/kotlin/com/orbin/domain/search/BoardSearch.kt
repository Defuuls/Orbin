package com.orbin.domain.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** What searching several boards found: every board's matches in board order, and what failed. */
data class BoardSearchOutcome<R>(
    val results: List<R>,
    val failedBoards: Int,
    /** The first board's error, for the message when nothing could be searched. */
    val firstError: Throwable?,
) {
    /** Every board failed: there is nothing to show but the error. */
    val allFailed: Boolean get() = failedBoards > 0 && results.isEmpty() && firstError != null
}

/**
 * Searches each board with [search], a few at a time, the way Android and iOS both search the
 * boards someone follows. A board that fails leaves the others' results; the outcome says how
 * many failed so the screen can decide whether that is an error. Cancellation always propagates.
 */
suspend fun <B, R> searchEachBoard(
    boards: List<B>,
    maxConcurrent: Int = MAX_CONCURRENT_BOARD_SEARCHES,
    search: suspend (B) -> List<R>,
): BoardSearchOutcome<R> {
    val gate = Semaphore(maxConcurrent)
    val perBoard =
        coroutineScope {
            boards.map { board -> async { gate.withPermit { attempt { search(board) } } } }.awaitAll()
        }
    val failures = perBoard.mapNotNull { it.exceptionOrNull() }
    return BoardSearchOutcome(
        results = perBoard.flatMap { it.getOrNull().orEmpty() },
        failedBoards = failures.size,
        firstError = failures.firstOrNull(),
    )
}

private suspend fun <R> attempt(block: suspend () -> R): Result<R> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (
        @Suppress("TooGenericExceptionCaught") error: Exception,
    ) {
        Result.failure(error)
    }

const val MAX_CONCURRENT_BOARD_SEARCHES = 4
