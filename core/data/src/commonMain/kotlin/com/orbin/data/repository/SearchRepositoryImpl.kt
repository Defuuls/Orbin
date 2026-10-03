package com.orbin.data.repository

import com.orbin.core.common.result.DataError
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.SavedSearch
import com.orbin.core.model.SearchQuery
import com.orbin.core.model.SearchResult
import com.orbin.core.model.SearchScope
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.core.model.matchesSearch
import com.orbin.core.model.toSearchResult
import com.orbin.data.database.dao.RecentSearchDao
import com.orbin.data.database.dao.SavedSearchDao
import com.orbin.data.database.entity.RecentSearchEntity
import com.orbin.data.database.entity.toEntity
import com.orbin.data.util.runCatchingProvider
import com.orbin.domain.repository.SearchRepository
import com.orbin.provider.api.ProviderRegistry
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val RECENT_LIMIT = 20

class SearchRepositoryImpl(
    private val registry: ProviderRegistry,
    private val recentSearchDao: RecentSearchDao,
    private val savedSearchDao: SavedSearchDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SearchRepository {
    override suspend fun search(query: SearchQuery): OrbinResult<List<SearchResult>> =
        withContext(ioDispatcher) {
            val provider =
                registry.get(query.provider)
                    ?: return@withContext OrbinResult.Failure(DataError.NotFound("Unknown provider"))

            runCatchingProvider {
                when (query.scope) {
                    SearchScope.REMOTE -> provider.search(query).filterNot { it.isPermanentlyFiltered() }
                    SearchScope.BOARD_CATALOG -> {
                        val board =
                            query.board
                                ?: return@runCatchingProvider emptyList()
                        provider
                            .getCatalog(CatalogRequest(query.provider, board))
                            .filterNot { it.isPermanentlyFiltered() }
                            .filter { it.matchesSearch(query) }
                            .map { it.toSearchResult() }
                    }
                    SearchScope.CURRENT_THREAD -> emptyList()
                }
            }
        }

    override fun observeRecentQueries(): Flow<List<String>> =
        recentSearchDao.observeRecent(RECENT_LIMIT).map { list -> list.map { it.query } }

    override suspend fun recordQuery(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        withContext(ioDispatcher) {
            recentSearchDao.upsert(
                RecentSearchEntity(provider = "", query = trimmed, lastUsedMillis = Clock.System.now().toEpochMilliseconds()),
            )
        }
    }

    override suspend fun clearRecentQueries() {
        withContext(ioDispatcher) { recentSearchDao.clear() }
    }

    override fun observeSavedSearches(): Flow<List<SavedSearch>> =
        savedSearchDao.observeAll().map { list -> list.map { it.toDomainModel() } }

    override suspend fun saveSearch(search: SavedSearch): Long =
        withContext(ioDispatcher) { savedSearchDao.save(search.toEntity()) }

    override suspend fun deleteSearch(id: Long) {
        withContext(ioDispatcher) { savedSearchDao.deleteById(id) }
    }
}
