package com.orbin.data.repository

import com.orbin.core.common.result.DataError
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SavedSearch
import com.orbin.core.model.SearchQuery
import com.orbin.core.model.SearchResult
import com.orbin.core.model.SearchScope
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.data.database.dao.RecentSearchDao
import com.orbin.data.database.dao.SavedSearchDao
import com.orbin.data.database.entity.RecentSearchEntity
import com.orbin.data.database.entity.SavedSearchEntity
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.api.ProviderRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SearchRepositoryTest {
    private val dispatcher = StandardTestDispatcher()

    private class TestRecentSearchDao : RecentSearchDao {
        private val searches = MutableStateFlow<List<RecentSearchEntity>>(emptyList())

        override fun observeRecent(limit: Int): Flow<List<RecentSearchEntity>> =
            searches.map { list -> list.take(limit) }

        override suspend fun upsert(entry: RecentSearchEntity) {
            searches.update { current ->
                listOf(entry) + current.filterNot { it.query == entry.query }
            }
        }

        override suspend fun clear() {
            searches.value = emptyList()
        }
    }

    private class TestSavedSearchDao : SavedSearchDao {
        private val saved = MutableStateFlow<List<SavedSearchEntity>>(emptyList())
        private var nextId = 1L

        override fun observeAll(): Flow<List<SavedSearchEntity>> = saved

        override suspend fun getById(id: Long): SavedSearchEntity? = saved.value.firstOrNull { it.id == id }

        override suspend fun save(entity: SavedSearchEntity): Long {
            val id = if (entity.id == 0L) nextId++ else entity.id
            val updated = entity.copy(id = id)
            saved.update { current ->
                listOf(updated) + current.filterNot { it.id == id }
            }
            return id
        }

        override suspend fun delete(entity: SavedSearchEntity) {
            deleteById(entity.id)
        }

        override suspend fun deleteById(id: Long) {
            saved.update { current -> current.filterNot { it.id == id } }
        }

        override suspend fun clear() {
            saved.value = emptyList()
        }
    }

    private class TestProvider(
        id: String,
        private val results: List<SearchResult> = emptyList(),
    ) : ImageBoardProvider {
        override val metadata = ProviderMetadata(ProviderId(id), "Test Provider", "https://example.org")
        override val capabilities = ProviderCapabilities(supportsSearch = true)

        override suspend fun getBoards() = emptyList<com.orbin.core.model.Board>()

        override suspend fun getCatalog(request: CatalogRequest) = emptyList<CatalogThread>()

        override suspend fun getThread(
            board: BoardId,
            thread: ThreadId,
        ): Thread = error("Not supported")

        override suspend fun search(query: SearchQuery): List<SearchResult> = results
    }

    private class TestRegistry(
        private val providers: Map<ProviderId, ImageBoardProvider> = emptyMap(),
    ) : ProviderRegistry {
        override fun all(): List<ImageBoardProvider> = providers.values.toList()

        override fun get(id: ProviderId): ImageBoardProvider? = providers[id]

        override fun default(): ImageBoardProvider = providers.values.firstOrNull() ?: error("No providers")
    }

    @Test
    fun recordQueryIgnoresBlankInput() =
        runTest(dispatcher) {
            val recentDao = TestRecentSearchDao()
            val savedDao = TestSavedSearchDao()
            val repository = SearchRepositoryImpl(TestRegistry(), recentDao, savedDao, dispatcher)

            repository.recordQuery("   ")
            assertTrue(repository.observeRecentQueries().first().isEmpty())
        }

    @Test
    fun recordQueryTrimsAndEmitsRecentSearch() =
        runTest(dispatcher) {
            val recentDao = TestRecentSearchDao()
            val savedDao = TestSavedSearchDao()
            val repository = SearchRepositoryImpl(TestRegistry(), recentDao, savedDao, dispatcher)

            repository.recordQuery("  kotlin multiplatform  ")
            val queries = repository.observeRecentQueries().first()
            assertEquals(listOf("kotlin multiplatform"), queries)

            repository.clearRecentQueries()
            assertTrue(repository.observeRecentQueries().first().isEmpty())
        }

    @Test
    fun searchWithUnknownProviderReturnsNotFound() =
        runTest(dispatcher) {
            val recentDao = TestRecentSearchDao()
            val savedDao = TestSavedSearchDao()
            val repository = SearchRepositoryImpl(TestRegistry(), recentDao, savedDao, dispatcher)

            val query = SearchQuery(ProviderId("nonexistent"), "query", SearchScope.REMOTE)
            val result = repository.search(query)

            assertIs<OrbinResult.Failure>(result)
            assertIs<DataError.NotFound>(result.error)
        }

    @Test
    fun searchRemoteFiltersPermanentlyBlockedContent() =
        runTest(dispatcher) {
            val recentDao = TestRecentSearchDao()
            val savedDao = TestSavedSearchDao()
            val providerId = ProviderId("test")
            val safeResult =
                SearchResult(
                    key = ThreadKey(providerId, BoardId("g"), ThreadId(1L)),
                    title = "Technology Discussion",
                    snippet = "Computers and programming",
                    matchedPost = PostId(1L),
                )
            val blockedResult =
                SearchResult(
                    key = ThreadKey(providerId, BoardId("g"), ThreadId(2L)),
                    title = "illegal content gore",
                    snippet = "violent text",
                    matchedPost = PostId(2L),
                )
            val provider = TestProvider("test", listOf(safeResult, blockedResult))
            val repository =
                SearchRepositoryImpl(
                    TestRegistry(mapOf(providerId to provider)),
                    recentDao,
                    savedDao,
                    dispatcher,
                )

            val result = repository.search(SearchQuery(providerId, "tech", SearchScope.REMOTE))
            assertIs<OrbinResult.Success<List<SearchResult>>>(result)
            assertEquals(1, result.data.size)
            assertEquals(safeResult, result.data.first())
        }

    @Test
    fun saveAndDeleteSearchRoundTripsCorrectly() =
        runTest(dispatcher) {
            val recentDao = TestRecentSearchDao()
            val savedDao = TestSavedSearchDao()
            val repository = SearchRepositoryImpl(TestRegistry(), recentDao, savedDao, dispatcher)

            val savedSearch =
                SavedSearch(
                    id = 0L,
                    text = "kotlin",
                    board = BoardId("g"),
                    createdAtMillis = 1000L,
                )

            val generatedId = repository.saveSearch(savedSearch)
            assertEquals(1L, generatedId)

            val active = repository.observeSavedSearches().first()
            assertEquals(1, active.size)
            assertEquals("kotlin", active.first().text)

            repository.deleteSearch(generatedId)
            assertTrue(repository.observeSavedSearches().first().isEmpty())
        }
}
