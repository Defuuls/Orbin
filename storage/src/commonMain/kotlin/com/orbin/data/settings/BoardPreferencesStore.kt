package com.orbin.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.orbin.core.model.BoardId
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.ProviderId
import com.orbin.domain.repository.BoardPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Favourite and followed boards, and per-board feed thread limits, kept in a Preferences
 * [DataStore]. Shared with iOS: Android's settings repository delegates here over its encrypted
 * settings store, iOS passes a DataStore of its own. The key names are the ones Android has
 * always written, so moving this code moved no stored data.
 */
class BoardPreferencesStore(
    private val dataStore: DataStore<Preferences>,
) : BoardPreferencesRepository {
    override fun observeFavoriteBoards(provider: ProviderId): Flow<Set<BoardId>> =
        dataStore.data.map { preferences -> preferences.boards(favoriteBoards(provider)) }

    override fun observeSubscribedBoards(provider: ProviderId): Flow<Set<BoardId>> =
        dataStore.data.map { preferences -> preferences.boards(subscribedBoards(provider)) }

    override suspend fun setFavoriteBoard(
        provider: ProviderId,
        board: BoardId,
        favorite: Boolean,
    ) = setBoardFlag(favoriteBoards(provider), board, favorite)

    override suspend fun setSubscribedBoard(
        provider: ProviderId,
        board: BoardId,
        subscribed: Boolean,
    ) = setBoardFlag(subscribedBoards(provider), board, subscribed)

    override fun observeFeedThreadLimit(
        provider: ProviderId,
        board: BoardId,
    ): Flow<FeedThreadLimit?> = dataStore.data.map { preferences -> preferences.limit(provider, board) }

    override fun observeFeedThreadLimits(
        provider: ProviderId,
        boards: Set<BoardId>,
    ): Flow<Map<BoardId, FeedThreadLimit>> =
        dataStore.data.map { preferences ->
            boards.mapNotNull { board -> preferences.limit(provider, board)?.let { board to it } }.toMap()
        }

    override suspend fun setFeedThreadLimit(
        provider: ProviderId,
        board: BoardId,
        limit: FeedThreadLimit?,
    ) {
        dataStore.edit { preferences ->
            val key = boardFeedThreadLimit(provider, board)
            if (limit != null) preferences[key] = limit.name else preferences.remove(key)
        }
    }

    private suspend fun setBoardFlag(
        key: Preferences.Key<Set<String>>,
        board: BoardId,
        enabled: Boolean,
    ) {
        dataStore.edit { preferences ->
            val current = preferences[key].orEmpty()
            preferences[key] = if (enabled) current + board.value else current - board.value
        }
    }

    private fun Preferences.boards(key: Preferences.Key<Set<String>>): Set<BoardId> =
        this[key].orEmpty().map(::BoardId).toSet()

    // A stored name that no longer matches an enum value reads as "no override" rather than failing.
    private fun Preferences.limit(
        provider: ProviderId,
        board: BoardId,
    ): FeedThreadLimit? =
        this[boardFeedThreadLimit(provider, board)]?.let { stored ->
            FeedThreadLimit.entries.firstOrNull { it.name == stored }
        }

    private companion object {
        fun favoriteBoards(provider: ProviderId): Preferences.Key<Set<String>> =
            stringSetPreferencesKey("favorite_boards_${provider.value}")

        fun subscribedBoards(provider: ProviderId): Preferences.Key<Set<String>> =
            stringSetPreferencesKey("subscribed_boards_${provider.value}")

        fun boardFeedThreadLimit(
            provider: ProviderId,
            board: BoardId,
        ): Preferences.Key<String> = stringPreferencesKey("feed_thread_limit_${provider.value}_${board.value}")
    }
}
