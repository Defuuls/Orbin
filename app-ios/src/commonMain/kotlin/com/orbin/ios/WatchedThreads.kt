package com.orbin.ios

import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadKey
import com.orbin.domain.repository.BookmarkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Watched threads on iOS, kept in the shared database through the repository Android uses.
 *
 * Android refreshes watched threads in the background. iOS has no such worker yet, so [refresh] does
 * the same work while the app is open: at launch, on returning to a tab and when the app comes to
 * the front. Opening a watched thread [reads][read] it, which clears its unread count.
 */
class WatchedThreads(
    private val bookmarks: BookmarkRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val fetch: suspend (ThreadKey) -> Thread,
) {
    private var refreshing: Job? = null
    private var refreshedAt: Long? = null

    /** Whether the thread [key] is bookmarked, which the thread screen shows as "watching". */
    fun watching(key: ThreadKey): Flow<Boolean> = bookmarks.observeBookmark(key).map { it != null }

    /** Watches [thread], or stops watching it: the thread screen's watch action, as on Android. */
    fun toggle(thread: Thread) {
        scope.launch {
            if (bookmarks.getBookmark(thread.key) != null) {
                bookmarks.removeBookmark(thread.key)
            } else {
                bookmarks.addBookmark(thread.toBookmark(now()))
            }
        }
    }

    /** New replies on the watched threads of [board] since each was last read, by thread number. */
    fun unread(board: SiteBoard): Flow<Map<Long, Int>> =
        bookmarks.observeBookmarks(board.provider, board.board.id).map { watched ->
            watched.filter { it.hasUnread }.associate { it.key.thread.value to it.unreadCount }
        }

    /**
     * Android's watch refresh: fetches every watched thread, at most [MAX_CONCURRENT_WATCH_REFRESHES]
     * at a time, and stores its reply count when it has grown, which is what the unread counts are
     * made of. A thread that fails to load keeps what it had. Runs at most once every
     * [WATCH_REFRESH_INTERVAL_MS], so switching tabs does not refetch the whole list each time.
     *
     * Returns the refresh: the one just started, the one still running, or the last one when it is
     * too soon for another.
     */
    fun refresh(): Job {
        val at = now()
        val previous = refreshing
        val tooSoon = refreshedAt?.let { at - it < WATCH_REFRESH_INTERVAL_MS } == true
        if (previous != null && (previous.isActive || tooSoon)) return previous
        refreshedAt = at
        return scope
            .launch {
                val watched = runCatching { bookmarks.watchedBookmarks() }.getOrDefault(emptyList())
                val gate = Semaphore(MAX_CONCURRENT_WATCH_REFRESHES)
                coroutineScope {
                    watched.forEach { bookmark ->
                        launch {
                            gate.withPermit {
                                runCatching { fetch(bookmark.key) }.onSuccess { thread ->
                                    val latest = thread.stats.replyCount
                                    if (latest > bookmark.latestReplyCount) {
                                        runCatching {
                                            bookmarks.updateLatest(
                                                bookmark.key,
                                                latest,
                                                thread.stats.isArchived,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }.also { refreshing = it }
    }

    /**
     * Marks the just-loaded [thread] read if it is watched, and returns the id of its first reply
     * that was new, for the thread screen's "jump to unread". A failed write only leaves the count.
     */
    suspend fun read(thread: Thread): String? {
        val bookmark = runCatching { bookmarks.getBookmark(thread.key) }.getOrNull() ?: return null
        runCatching {
            bookmarks.updateLatest(thread.key, thread.stats.replyCount, thread.stats.isArchived)
            bookmarks.markRead(thread.key)
        }
        return thread.replies
            .getOrNull(bookmark.lastSeenReplyCount)
            ?.id
            ?.value
            ?.toString()
    }
}

/** Android's watch worker refreshes at most this many threads at once; so does iOS. */
private const val MAX_CONCURRENT_WATCH_REFRESHES = 3

/** The shortest gap between two watch refreshes. */
internal const val WATCH_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
