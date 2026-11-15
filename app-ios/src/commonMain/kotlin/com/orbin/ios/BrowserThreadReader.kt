package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.withNsfwMediaCovered
import com.orbin.core.model.withViolentMediaCovered
import com.orbin.domain.filter.ContentPolicy
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SavedThreadRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Loading, privacy and offline retention share one path for threads opened from any source. */
internal class BrowserThreadReader(
    private val settings: () -> AppSettings,
    private val board: (ThreadKey) -> Board?,
    private val fetch: suspend (ThreadKey) -> Thread,
    private val copies: SavedThreadRepository?,
    private val history: HistoryRepository,
    private val dispatcher: CoroutineDispatcher,
    private val now: () -> Long,
) {
    suspend fun load(key: ThreadKey): Thread {
        val loaded = liveOrCopy(key)
        val options = settings()
        val boardIsNsfw = board(key)?.isNsfw != false
        if (options.nsfwContentHidden && boardIsNsfw) throw ContentBlocked()
        val visible = withContext(dispatcher) { ContentPolicy.visibleThread(loaded) } ?: throw ContentBlocked()
        currentCoroutineContext().ensureActive()
        recordVisit(visible)
        // The repository independently checks bookmarks and persistent history before retaining.
        cacheLastLoaded(visible)
        return visible
            .withViolentMediaCovered(options.coverViolentMedia)
            .withNsfwMediaCovered(options.coverNsfwMedia && boardIsNsfw)
    }

    private suspend fun liveOrCopy(key: ThreadKey): Thread =
        try {
            fetch(key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            // Providers may wrap transport/parser failures; only an existing local copy is used.
            @Suppress("TooGenericExceptionCaught") failure: Exception,
        ) {
            copies?.load(key)?.copy(isOfflineCopy = true) ?: throw failure
        }

    /** A failed history write must not make an otherwise readable thread fail to open. */
    private suspend fun recordVisit(thread: Thread) {
        if (!settings().saveBrowsingHistory) return
        val timestamp = now()
        runCatching { history.record(withContext(dispatcher) { thread.toHistoryEntry(timestamp) }) }
            .onFailure { if (it is CancellationException) throw it }
    }

    private suspend fun cacheLastLoaded(thread: Thread) {
        runCatching { copies?.cacheLastLoaded(thread) }.onFailure { if (it is CancellationException) throw it }
    }
}

/** A permanent policy block remains distinct from a transport failure or an offline copy. */
internal class ContentBlocked : Exception(ContentPolicy.BLOCKED_THREAD_MESSAGE)
