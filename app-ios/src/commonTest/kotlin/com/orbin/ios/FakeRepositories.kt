package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadKey
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Bookmarks kept in memory. The Room-backed one is tested against real SQLite in `:storage`. */
class FakeBookmarks : BookmarkRepository {
    val saved = MutableStateFlow<Map<ThreadKey, Bookmark>>(emptyMap())

    override fun observeBookmarks(): Flow<List<Bookmark>> = saved.map { it.values.toList() }

    override fun observeBookmark(key: ThreadKey): Flow<Bookmark?> = saved.map { it[key] }

    override suspend fun getBookmark(key: ThreadKey): Bookmark? = saved.value[key]

    override suspend fun addBookmark(bookmark: Bookmark) = saved.update { it + (bookmark.key to bookmark) }

    override suspend fun removeBookmark(key: ThreadKey) = saved.update { it - key }

    override suspend fun setWatched(
        key: ThreadKey,
        watched: Boolean,
    ) = saved.update { all -> all[key]?.let { all + (key to it.copy(isWatched = watched)) } ?: all }

    override suspend fun markRead(key: ThreadKey) =
        saved.update { all ->
            all[key]?.let { all + (key to it.copy(lastSeenReplyCount = it.latestReplyCount)) } ?: all
        }

    override suspend fun watchedBookmarks(): List<Bookmark> = saved.value.values.filter { it.isWatched }

    override suspend fun updateLatest(
        key: ThreadKey,
        latestReplyCount: Int,
        isThreadDead: Boolean,
    ) = saved.update { all ->
        all[key]?.let { all + (key to it.copy(latestReplyCount = latestReplyCount, isThreadDead = isThreadDead)) }
            ?: all
    }
}

/** Reading history kept in memory. */
class FakeHistory : HistoryRepository {
    val entries = MutableStateFlow<Map<ThreadKey, HistoryEntry>>(emptyMap())

    override fun observeHistory(): Flow<List<HistoryEntry>> = entries.map { it.values.toList() }

    override fun observeVisitedKeys(): Flow<Set<ThreadKey>> = entries.map { it.keys }

    override suspend fun getEntry(key: ThreadKey): HistoryEntry? = entries.value[key]

    override suspend fun record(entry: HistoryEntry) = entries.update { it + (entry.key to entry) }

    override suspend fun updateScrollPosition(
        key: ThreadKey,
        postId: PostId,
        offsetPx: Int,
    ) = Unit

    override suspend fun clear() = entries.update { emptyMap() }
}

/** Followed boards and feed limits kept in memory. The DataStore-backed store is tested in `:storage`. */
class FakeBoardPreferences : BoardPreferencesRepository {
    val subscribed = MutableStateFlow<Map<ProviderId, Set<BoardId>>>(emptyMap())
    val limits = MutableStateFlow<Map<Pair<ProviderId, BoardId>, FeedThreadLimit>>(emptyMap())

    override fun observeFavoriteBoards(provider: ProviderId): Flow<Set<BoardId>> = MutableStateFlow(emptySet())

    override fun observeSubscribedBoards(provider: ProviderId): Flow<Set<BoardId>> =
        subscribed.map { it[provider].orEmpty() }

    override suspend fun setFavoriteBoard(
        provider: ProviderId,
        board: BoardId,
        favorite: Boolean,
    ) = Unit

    override suspend fun setSubscribedBoard(
        provider: ProviderId,
        board: BoardId,
        subscribed: Boolean,
    ) = this.subscribed.update { all ->
        val current = all[provider].orEmpty()
        all + (provider to if (subscribed) current + board else current - board)
    }

    override fun observeFeedThreadLimit(
        provider: ProviderId,
        board: BoardId,
    ): Flow<FeedThreadLimit?> = limits.map { it[provider to board] }

    override suspend fun setFeedThreadLimit(
        provider: ProviderId,
        board: BoardId,
        limit: FeedThreadLimit?,
    ) = limits.update { all -> if (limit == null) all - (provider to board) else all + ((provider to board) to limit) }
}

/** App settings kept in memory. The DataStore-backed store is tested in `:storage`. */
@Suppress("TooManyFunctions")
class FakeSettings : SettingsRepository {
    override val settings = MutableStateFlow(AppSettings.Default)

    override suspend fun setHideNsfwBoards(enabled: Boolean) = settings.update { it.copy(hideNsfwBoards = enabled) }

    override suspend fun setDeepMediaScan(enabled: Boolean) = settings.update { it.copy(deepMediaScan = enabled) }

    override suspend fun setThemeMode(mode: AppThemeMode) = settings.update { it.copy(themeMode = mode) }

    override suspend fun setAmoled(enabled: Boolean) = settings.update { it.copy(amoled = enabled) }

    override suspend fun setBiometricLockEnabled(enabled: Boolean) =
        settings.update {
            it.copy(biometricLockEnabled = enabled)
        }

    override suspend fun setOnboardingCompleted(completed: Boolean) =
        settings.update {
            it.copy(onboardingCompleted = completed)
        }

    override suspend fun setActiveProviderId(id: ProviderId) = settings.update { it.copy(activeProviderId = id.value) }
}
