package com.orbin.domain.repository

import androidx.paging.PagingData
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.CatalogSort
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SavedSearch
import com.orbin.core.model.SearchQuery
import com.orbin.core.model.SearchResult
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import kotlinx.coroutines.flow.Flow

/**
 * Repository contracts owned by the domain layer and implemented by `:data`. The domain depends
 * only on these interfaces, never on concrete data sources — that's what keeps the architecture
 * inverted and testable.
 */

interface BoardRepository {
    /** Boards for [provider], served from cache and refreshed in the background. */
    fun observeBoards(provider: ProviderId): Flow<List<Board>>

    suspend fun refreshBoards(provider: ProviderId): OrbinResult<List<Board>>

    suspend fun getBoard(
        provider: ProviderId,
        board: BoardId,
    ): OrbinResult<Board>
}

interface BoardPreferencesRepository {
    fun observeFavoriteBoards(provider: ProviderId): Flow<Set<BoardId>>

    fun observeSubscribedBoards(provider: ProviderId): Flow<Set<BoardId>>

    suspend fun setFavoriteBoard(
        provider: ProviderId,
        board: BoardId,
        favorite: Boolean,
    )

    suspend fun setSubscribedBoard(
        provider: ProviderId,
        board: BoardId,
        subscribed: Boolean,
    )

    /** Per-board override for how many threads the subscribed feed shows; null uses the global default. */
    fun observeFeedThreadLimit(
        provider: ProviderId,
        board: BoardId,
    ): Flow<FeedThreadLimit?>

    suspend fun setFeedThreadLimit(
        provider: ProviderId,
        board: BoardId,
        limit: FeedThreadLimit?,
    )
}

interface CatalogRepository {
    /** A Paging stream of catalog threads for a board. */
    fun catalogStream(
        provider: ProviderId,
        board: BoardId,
        sort: CatalogSort,
    ): Flow<PagingData<CatalogThread>>
}

interface ThreadRepository {
    /**
     * Loads a thread, returning a stream so the UI updates when a background refresh brings new
     * replies. The first emission may come from cache for instant display.
     *
     * Set [forceRefresh] to skip the cache and go to the network. A thread accumulates replies
     * continuously, so a reader who explicitly asks for new posts must not be served the same
     * snapshot back for the remainder of the cache lifetime.
     */
    fun observeThread(
        key: ThreadKey,
        forceRefresh: Boolean = false,
    ): Flow<OrbinResult<Thread>>

    suspend fun refreshThread(
        provider: ProviderId,
        board: BoardId,
        thread: ThreadId,
        forceRefresh: Boolean = false,
    ): OrbinResult<Thread>
}

interface BookmarkRepository {
    fun observeBookmarks(): Flow<List<Bookmark>>

    fun observeBookmark(key: ThreadKey): Flow<Bookmark?>

    suspend fun addBookmark(bookmark: Bookmark)

    suspend fun removeBookmark(key: ThreadKey)

    suspend fun setWatched(
        key: ThreadKey,
        watched: Boolean,
    )

    /** Marks a thread read up to its current reply count. */
    suspend fun markRead(key: ThreadKey)

    /** All bookmarks the user is watching (for the background update worker). */
    suspend fun watchedBookmarks(): List<Bookmark>

    /** Updates the latest known reply count and dead flag from a background refresh. */
    suspend fun updateLatest(
        key: ThreadKey,
        latestReplyCount: Int,
        isThreadDead: Boolean,
    )
}

interface HistoryRepository {
    fun observeHistory(): Flow<List<HistoryEntry>>

    /** Every visited thread key, for "already read" styling in catalogs and feeds. */
    fun observeVisitedKeys(): Flow<Set<ThreadKey>>

    suspend fun getEntry(key: ThreadKey): HistoryEntry?

    /** Records a visit. Preserves any existing scroll position rather than resetting it. */
    suspend fun record(entry: HistoryEntry)

    /** Persists where the reader last scrolled to, so reopening the thread can resume there. */
    suspend fun updateScrollPosition(
        key: ThreadKey,
        postId: PostId,
        offsetPx: Int,
    )

    suspend fun clear()
}

interface SearchRepository {
    suspend fun search(query: SearchQuery): OrbinResult<List<SearchResult>>

    fun observeRecentQueries(): Flow<List<String>>

    suspend fun recordQuery(text: String)

    suspend fun clearRecentQueries()

    fun observeSavedSearches(): Flow<List<SavedSearch>>

    suspend fun saveSearch(search: SavedSearch): Long

    suspend fun deleteSearch(id: Long)

    suspend fun clearSavedSearches()
}
