package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.Bookmark
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadAvailability
import com.orbin.core.model.ThreadKey
import com.orbin.domain.bookmarks.BookmarkStatusRefresher
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.InMemoryThreadMemoryRepository
import com.orbin.domain.repository.ThreadMemoryRepository
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Local save/watch actions need catalog identity only and never fetch full threads. */
internal class LocalThreadCollections(
    private val bookmarks: BookmarkRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val onWatch: () -> Unit,
    providers: List<ImageBoardProvider> = emptyList(),
    private val history: HistoryRepository? = null,
    private val memory: ThreadMemoryRepository = InMemoryThreadMemoryRepository(),
    private val preferences: () -> AppSettings = { AppSettings.Default },
) {
    private val mutations = Mutex()
    private val statuses =
        BookmarkStatusRefresher(bookmarks, { key ->
            val provider =
                providers.firstOrNull { it.metadata.id == key.provider }
                    ?: throw ProviderException.Unsupported("The source is unavailable")
            provider.getThread(key.board, key.thread)
        }, now)
    val isRefreshing = statuses.isRefreshing

    fun refreshStatuses(force: Boolean = false) {
        scope.launch { statuses.refreshAll(force) }
    }

    fun remove(
        key: ThreadKey,
        fromHistory: Boolean,
    ) {
        scope.launch {
            if (fromHistory) history?.remove(key) else bookmarks.removeBookmark(key)
        }
    }

    fun clear(
        fromHistory: Boolean,
        watchedOnly: Boolean = false,
    ) {
        scope.launch {
            if (fromHistory) {
                history?.clear()
            } else {
                bookmarks
                    .observeBookmarks()
                    .first()
                    .filter { !watchedOnly || it.isWatched }
                    .forEach { bookmarks.removeBookmark(it.key) }
            }
        }
    }

    fun removeDeleted(watchedOnly: Boolean = false) {
        scope.launch {
            bookmarks
                .observeBookmarks()
                .first()
                .filter {
                    it.availability == ThreadAvailability.DELETED && (!watchedOnly || it.isWatched)
                }.forEach { bookmarks.removeBookmark(it.key) }
        }
    }

    fun markRead(key: ThreadKey) {
        scope.launch {
            val latest = bookmarks.getBookmark(key)?.latestReplyCount ?: 0
            memory.update(
                key,
            ) { it.copy(seenPostIds = it.seenPostIds + it.knownPostIds, acknowledgedReplyCount = latest) }
            if (!preferences().historySessionOnly) bookmarks.markRead(key)
        }
    }

    fun markAllRead(watchedOnly: Boolean = false) {
        scope.launch {
            bookmarks.observeBookmarks().first().filter { !watchedOnly || it.isWatched }.forEach { bookmark ->
                memory.update(bookmark.key) {
                    it.copy(
                        seenPostIds = it.seenPostIds + it.knownPostIds,
                        acknowledgedReplyCount = bookmark.latestReplyCount,
                    )
                }
                if (!preferences().historySessionOnly) bookmarks.markRead(bookmark.key)
            }
        }
    }

    /** Saved membership is separate from whether background thread updates are enabled. */
    fun saved(key: ThreadKey): Flow<Boolean> = bookmarks.observeBookmark(key).map { it != null }

    /** Watching keeps its saved record; turn watching off before removing the bookmark. */
    fun toggleSaved(thread: Thread) {
        Haptics.light()
        scope.launch {
            mutations.withLock {
                val existing = bookmarks.getBookmark(thread.key)
                if (existing == null) {
                    bookmarks.addBookmark(thread.toBookmark(now()).copy(isWatched = false))
                } else if (!existing.isWatched) {
                    bookmarks.removeBookmark(thread.key)
                }
            }
        }
    }

    /** A watched thread stays saved until watching is disabled, preserving its update state. */
    fun toggleFeedSaved(feed: FeedThread) {
        Haptics.light()
        scope.launch {
            mutations.withLock {
                val existing = bookmarks.getBookmark(feed.thread.key)
                if (existing == null) {
                    bookmarks.addBookmark(feed.bookmark(watch = false))
                } else if (!existing.isWatched) {
                    bookmarks.removeBookmark(feed.thread.key)
                }
            }
        }
    }

    /** Watching an already saved thread keeps the bookmark when updates are turned off. */
    fun toggleFeedWatch(feed: FeedThread) {
        Haptics.light()
        scope.launch {
            mutations.withLock {
                val existing = bookmarks.getBookmark(feed.thread.key)
                if (existing == null) {
                    bookmarks.addBookmark(feed.bookmark(watch = true))
                    onWatch()
                } else {
                    bookmarks.setWatched(existing.key, !existing.isWatched)
                    if (!existing.isWatched) onWatch()
                }
            }
        }
    }

    /** A media double tap only enables watching; repeated taps can never turn it off. */
    fun watchFeed(feed: FeedThread) {
        Haptics.light()
        scope.launch {
            mutations.withLock {
                val existing = bookmarks.getBookmark(feed.thread.key)
                if (existing == null) {
                    bookmarks.addBookmark(feed.bookmark(watch = true))
                    onWatch()
                } else if (!existing.isWatched) {
                    bookmarks.setWatched(existing.key, true)
                    onWatch()
                }
            }
        }
    }

    private fun FeedThread.bookmark(watch: Boolean) =
        Bookmark(
            key = thread.key,
            title = thread.originalPost.subject?.takeIf { it.isNotBlank() } ?: "/${thread.key.board.value}/",
            thumbnailUrl =
                thread.originalPost.attachments
                    .firstOrNull()
                    ?.thumbnailUrl,
            createdAtMillis = now(),
            isWatched = watch,
            lastSeenReplyCount = thread.stats.replyCount,
            latestReplyCount = thread.stats.replyCount,
        )
}

internal fun Browser.siteName(id: ProviderId): String = sites.firstOrNull { it.id == id }?.name ?: id.value

internal fun Browser.saved(key: ThreadKey): Flow<Boolean> = collections.saved(key)

internal fun Browser.toggleSaved(thread: Thread) = collections.toggleSaved(thread)

internal fun Browser.toggleFeedSaved(feed: FeedThread) = collections.toggleFeedSaved(feed)

internal fun Browser.toggleFeedWatch(feed: FeedThread) = collections.toggleFeedWatch(feed)

internal fun Browser.watchFeed(feed: FeedThread) = collections.watchFeed(feed)
