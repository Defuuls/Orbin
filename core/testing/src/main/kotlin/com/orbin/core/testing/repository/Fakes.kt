package com.orbin.core.testing.repository

import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SearchQuery
import com.orbin.core.model.SearchResult
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.domain.repository.SearchRepository
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.api.ProviderRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Minimal [ImageBoardProvider] for tests; only metadata/capabilities and search are meaningful. */
class FakeImageBoardProvider(
    id: String = "fourchan",
    displayName: String = "Test Provider",
    private val searchResults: List<SearchResult> = emptyList(),
) : ImageBoardProvider {
    override val metadata = ProviderMetadata(ProviderId(id), displayName, "https://example.org")
    override val capabilities = ProviderCapabilities(supportsSearch = true)

    override suspend fun getBoards(): List<Board> = emptyList()

    override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> = emptyList()

    override suspend fun getThread(
        board: BoardId,
        thread: ThreadId,
    ): Thread = throw ProviderException.NotFound("not used in tests")

    override suspend fun search(query: SearchQuery): List<SearchResult> = searchResults
}

/** [ProviderRegistry] wrapping a single [provider]. */
class FakeProviderRegistry(
    private val provider: ImageBoardProvider = FakeImageBoardProvider(),
) : ProviderRegistry {
    override fun all(): List<ImageBoardProvider> = listOf(provider)

    override fun get(id: ProviderId): ImageBoardProvider? = provider.takeIf { it.metadata.id == id }

    override fun default(): ImageBoardProvider = provider
}

/** In-memory [SearchRepository] returning preset [results] and recording queries. */
class FakeSearchRepository(
    private val results: List<SearchResult> = emptyList(),
) : SearchRepository {
    private val recents = MutableStateFlow<List<String>>(emptyList())

    override suspend fun search(query: SearchQuery): OrbinResult<List<SearchResult>> = OrbinResult.Success(results)

    override fun observeRecentQueries(): Flow<List<String>> = recents

    override suspend fun recordQuery(text: String) {
        recents.value = (listOf(text) + recents.value).distinct()
    }

    override suspend fun clearRecentQueries() {
        recents.value = emptyList()
    }
}
