package com.orbin.data.repository

import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.core.common.result.DataError
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaType
import com.orbin.core.model.SearchContentType
import com.orbin.core.model.SearchQuery
import com.orbin.core.model.SearchResult
import com.orbin.core.model.SearchScope
import com.orbin.core.model.SavedSearch
import com.orbin.data.database.dao.RecentSearchDao
import com.orbin.data.database.dao.SavedSearchDao
import com.orbin.data.database.entity.RecentSearchEntity
import com.orbin.data.database.entity.SavedSearchEntity
import com.orbin.data.database.entity.toEntity
import com.orbin.data.util.runCatchingProvider
import com.orbin.domain.repository.SearchRepository
import com.orbin.provider.api.ProviderRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val RECENT_LIMIT = 20
private const val SNIPPET_MAX = 160

/**
 * Search over a board's catalog (client-side), with server-side search delegated to the provider
 * when it advertises the capability. Recent queries are persisted in Room. Designed so adding a
 * server-side search provider is a matter of the provider returning results for [SearchScope.REMOTE].
 */
@Singleton
class SearchRepositoryImpl
    @Inject
    constructor(
        private val registry: ProviderRegistry,
        private val recentSearchDao: RecentSearchDao,
        private val savedSearchDao: SavedSearchDao,
        @Dispatcher(OrbinDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    ) : SearchRepository {
        override suspend fun search(query: SearchQuery): OrbinResult<List<SearchResult>> =
            withContext(ioDispatcher) {
                val provider =
                    registry.get(query.provider)
                        ?: return@withContext OrbinResult.Failure(DataError.NotFound("Unknown provider"))

                runCatchingProvider {
                    when (query.scope) {
                        SearchScope.REMOTE -> provider.search(query)
                        SearchScope.BOARD_CATALOG -> {
                            val board =
                                query.board
                                    ?: return@runCatchingProvider emptyList()
                            provider
                                .getCatalog(CatalogRequest(query.provider, board))
                                .filter { it.matches(query) }
                                .map { it.toSearchResult() }
                        }
                        // Current-thread search is done in the thread feature over already-loaded posts.
                        SearchScope.CURRENT_THREAD -> emptyList()
                    }
                }
            }

        override fun observeRecentQueries(): Flow<List<String>> =
            recentSearchDao.observeRecent(RECENT_LIMIT).map { list -> list.map { it.query } }

        override suspend fun recordQuery(text: String) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return
            recentSearchDao.upsert(
                RecentSearchEntity(provider = "", query = trimmed, lastUsedMillis = System.currentTimeMillis()),
            )
        }

        override suspend fun clearRecentQueries() = recentSearchDao.clear()

        override fun observeSavedSearches(): Flow<List<SavedSearch>> =
            savedSearchDao.observeAll().map { list -> list.map { it.toDomainModel() } }

        override suspend fun saveSearch(search: SavedSearch): Long =
            withContext(ioDispatcher) { savedSearchDao.save(search.toEntity()) }

        override suspend fun deleteSearch(id: Long) {
            withContext(ioDispatcher) { savedSearchDao.deleteById(id) }
        }

        override suspend fun clearSavedSearches() {
            withContext(ioDispatcher) { savedSearchDao.clear() }
        }

        private fun CatalogThread.matches(query: SearchQuery): Boolean {
            val needle = query.text.trim().lowercase()
            val subject = originalPost.subject?.lowercase().orEmpty()
            val comment = originalPost.comment.raw.lowercase()
            val textMatches = needle in subject || needle in comment
            val filters = query.filters
            val mediaMatches = !filters.mediaOnly || originalPost.attachments.isNotEmpty()
            val contentMatches =
                filters.contentTypes.isEmpty() ||
                    filters.contentTypes.any { type -> matchesContentType(type) }
            return needle.isEmpty() || (textMatches && mediaMatches && contentMatches)
        }

        private fun CatalogThread.matchesContentType(type: SearchContentType): Boolean =
            when (type) {
                SearchContentType.POST -> true
                SearchContentType.IMAGE ->
                    originalPost.attachments.any { it.type == MediaType.IMAGE || it.type == MediaType.ANIMATED_IMAGE }
                SearchContentType.VIDEO -> originalPost.attachments.any { it.type == MediaType.VIDEO }
                SearchContentType.AUDIO -> originalPost.attachments.any { it.type == MediaType.AUDIO }
                SearchContentType.URL -> URL_PATTERN.containsMatchIn(originalPost.comment.raw)
            }

        private companion object {
            val URL_PATTERN = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
        }

        private fun CatalogThread.toSearchResult(): SearchResult =
            SearchResult(
                key = key,
                title = originalPost.subject ?: "/${key.board.value}/",
                snippet = originalPost.comment.raw.take(SNIPPET_MAX),
                matchedPost = originalPost.id,
                thumbnailUrl = originalPost.attachments.firstOrNull()?.thumbnailUrl,
            )
    }
