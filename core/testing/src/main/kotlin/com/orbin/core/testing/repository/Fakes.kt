package com.orbin.core.testing.repository

import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SavedSearch
import com.orbin.core.model.SearchQuery
import com.orbin.core.model.SearchResult
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.SearchRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.api.ProviderRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

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
    private val saved = MutableStateFlow<List<SavedSearch>>(emptyList())
    private var nextSavedId = 1L

    override suspend fun search(query: SearchQuery): OrbinResult<List<SearchResult>> = OrbinResult.Success(results)

    override fun observeRecentQueries(): Flow<List<String>> = recents

    override suspend fun recordQuery(text: String) {
        recents.value = (listOf(text) + recents.value).distinct()
    }

    override suspend fun clearRecentQueries() {
        recents.value = emptyList()
    }

    override fun observeSavedSearches(): Flow<List<SavedSearch>> = saved

    override suspend fun saveSearch(search: SavedSearch): Long {
        val id = nextSavedId++
        val saved = search.copy(id = id)
        this.saved.value = this.saved.value + saved
        return id
    }

    override suspend fun deleteSearch(id: Long) {
        saved.value = saved.value.filterNot { it.id == id }
    }
}

class FakeBoardRepository(
    private val boards: List<Board> = listOf(Board(BoardId("g"), "Technology")),
) : BoardRepository {
    override fun observeBoards(provider: ProviderId): Flow<List<Board>> = flowOf(boards)

    override suspend fun refreshBoards(provider: ProviderId): OrbinResult<List<Board>> = OrbinResult.Success(boards)
}

class FakeBoardPreferencesRepository(
    private val subscribed: Set<BoardId> = setOf(BoardId("g")),
    private val favorites: Set<BoardId> = emptySet(),
) : BoardPreferencesRepository {
    private val threadLimits = MutableStateFlow<Map<BoardId, FeedThreadLimit?>>(emptyMap())

    override fun observeFavoriteBoards(provider: ProviderId): Flow<Set<BoardId>> = flowOf(favorites)

    override fun observeSubscribedBoards(provider: ProviderId): Flow<Set<BoardId>> = flowOf(subscribed)

    override suspend fun setFavoriteBoard(
        provider: ProviderId,
        board: BoardId,
        favorite: Boolean,
    ) = Unit

    override suspend fun setSubscribedBoard(
        provider: ProviderId,
        board: BoardId,
        subscribed: Boolean,
    ) = Unit

    override fun observeFeedThreadLimit(
        provider: ProviderId,
        board: BoardId,
    ): Flow<FeedThreadLimit?> = threadLimits.map { it[board] }

    override suspend fun setFeedThreadLimit(
        provider: ProviderId,
        board: BoardId,
        limit: FeedThreadLimit?,
    ) {
        threadLimits.update { it + (board to limit) }
    }
}

@Suppress("TooManyFunctions")
class FakeSettingsRepository(
    initial: AppSettings = AppSettings.Default,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state

    override suspend fun setHideNsfwBoards(enabled: Boolean) {
        update { copy(hideNsfwBoards = enabled) }
    }

    override suspend fun setDeepMediaScan(enabled: Boolean) {
        update { copy(deepMediaScan = enabled) }
    }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        update { copy(themeMode = mode) }
    }

    override suspend fun setAmoled(enabled: Boolean) {
        update { copy(amoled = enabled) }
    }

    override suspend fun setBiometricLockEnabled(enabled: Boolean) {
        update { copy(biometricLockEnabled = enabled) }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        update { copy(onboardingCompleted = completed) }
    }

    override suspend fun setActiveProviderId(id: ProviderId) {
        update { copy(activeProviderId = id.value) }
    }

    /** The settings as they stand now, for asserting that a screen wrote through. */
    val current: AppSettings get() = state.value

    private fun update(block: AppSettings.() -> AppSettings) {
        state.update { it.block() }
    }
}
