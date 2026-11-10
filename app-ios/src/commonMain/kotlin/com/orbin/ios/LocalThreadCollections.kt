package com.orbin.ios

import com.orbin.core.model.Bookmark
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadKey
import com.orbin.domain.repository.BookmarkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Local save/watch actions need catalog identity only and never fetch full threads. */
internal class LocalThreadCollections(
    private val bookmarks: BookmarkRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val onWatch: () -> Unit,
) {
    /** Saved membership is separate from whether background thread updates are enabled. */
    fun saved(key: ThreadKey): Flow<Boolean> = bookmarks.observeBookmark(key).map { it != null }

    /** Watching keeps its saved record; turn watching off before removing the bookmark. */
    fun toggleSaved(thread: Thread) {
        Haptics.light()
        scope.launch {
            val existing = bookmarks.getBookmark(thread.key)
            if (existing == null) {
                bookmarks.addBookmark(thread.toBookmark(now()).copy(isWatched = false))
            } else if (!existing.isWatched) {
                bookmarks.removeBookmark(thread.key)
            }
        }
    }

    /** A watched thread stays saved until watching is disabled, preserving its update state. */
    fun toggleFeedSaved(feed: FeedThread) {
        Haptics.light()
        scope.launch {
            val existing = bookmarks.getBookmark(feed.thread.key)
            if (existing == null) {
                bookmarks.addBookmark(feed.bookmark(watch = false))
            } else if (!existing.isWatched) {
                bookmarks.removeBookmark(feed.thread.key)
            }
        }
    }

    /** Watching an already saved thread keeps the bookmark when updates are turned off. */
    fun toggleFeedWatch(feed: FeedThread) {
        Haptics.light()
        scope.launch {
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
