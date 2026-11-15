package com.orbin.domain.bookmarks

import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadAvailability
import com.orbin.core.model.ThreadKey
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.provider.api.ProviderException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Clock

/** Status checks use the live provider, never an offline copy mistaken for a current response. */
class BookmarkStatusRefresher(
    private val bookmarks: BookmarkRepository,
    private val fetch: suspend (ThreadKey) -> Thread,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutex = Mutex()
    private val refreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = refreshing.asStateFlow()
    private var lastCheckedAt: Long? = null
    private var lastResult = BookmarkRefreshResult()

    suspend fun refreshAll(force: Boolean = false): BookmarkRefreshResult {
        if (!mutex.tryLock()) return lastResult
        try {
            val started = now()
            if (!force &&
                lastCheckedAt?.let { started - it in 0 until REFRESH_INTERVAL_MILLIS } == true
            ) {
                return lastResult
            }
            refreshing.value = true
            var checked = 0
            var failed = 0
            for (bookmark in bookmarks.observeBookmarks().first()) {
                if (refreshOne(bookmark.key)) checked++ else failed++
            }
            lastCheckedAt = started
            lastResult = BookmarkRefreshResult(checked, failed)
            return lastResult
        } finally {
            refreshing.value = false
            mutex.unlock()
        }
    }

    private suspend fun refreshOne(key: ThreadKey): Boolean {
        val result =
            runCatching {
                val thread = fetch(key)
                require(thread.key == key) { "A provider returned a different thread" }
                bookmarks.updateLatest(key, thread.stats.replyCount, thread.stats.isArchived)
                bookmarks.updateAvailability(
                    key,
                    if (thread.stats.isArchived) ThreadAvailability.ARCHIVED else ThreadAvailability.LIVE,
                    now(),
                )
            }
        return result.fold(onSuccess = { true }, onFailure = { recordFailure(key, it) })
    }

    private suspend fun recordFailure(
        key: ThreadKey,
        failure: Throwable,
    ): Boolean {
        val deleted =
            when (failure) {
                is CancellationException -> throw failure
                is ProviderException.NotFound -> true
                is ProviderException.Http -> failure.code == HTTP_NOT_FOUND || failure.code == HTTP_GONE
                is Exception -> false
                else -> throw failure
            }
        if (deleted) bookmarks.updateAvailability(key, ThreadAvailability.DELETED, now())
        // Offline, rate limits and parse failures do not prove a thread was deleted.
        return deleted
    }

    private companion object {
        const val REFRESH_INTERVAL_MILLIS = 5 * 60 * 1000L
        const val HTTP_NOT_FOUND = 404
        const val HTTP_GONE = 410
    }
}

data class BookmarkRefreshResult(
    val checked: Int = 0,
    val failed: Int = 0,
)
