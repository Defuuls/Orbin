package com.orbin.core.testing.repository

import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppIconVariant
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.ColorTheme
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.PreloadOption
import com.orbin.core.model.PreloadThrottleMode
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SearchQuery
import com.orbin.core.model.SearchResult
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThumbnailSize
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

    override suspend fun search(query: SearchQuery): OrbinResult<List<SearchResult>> = OrbinResult.Success(results)

    override fun observeRecentQueries(): Flow<List<String>> = recents

    override suspend fun recordQuery(text: String) {
        recents.value = (listOf(text) + recents.value).distinct()
    }

    override suspend fun clearRecentQueries() {
        recents.value = emptyList()
    }
}

class FakeBoardRepository(
    private val boards: List<Board> = listOf(Board(BoardId("g"), "Technology")),
) : BoardRepository {
    override fun observeBoards(provider: ProviderId): Flow<List<Board>> = flowOf(boards)

    override suspend fun refreshBoards(provider: ProviderId): OrbinResult<List<Board>> = OrbinResult.Success(boards)

    override suspend fun getBoard(
        provider: ProviderId,
        board: BoardId,
    ): OrbinResult<Board> =
        boards
            .firstOrNull { it.id == board }
            ?.let { OrbinResult.Success(it) }
            ?: OrbinResult.Failure(
                com.orbin.core.common.result.DataError
                    .NotFound("Board not found"),
            )
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

    override suspend fun setPersonalizedHomeFeed(enabled: Boolean) {
        update { copy(personalizedHomeFeed = enabled) }
    }

    override suspend fun setHiddenTags(tags: String) {
        update { copy(hiddenTags = tags) }
    }

    override suspend fun setMutedTags(tags: String) {
        update { copy(mutedTags = tags) }
    }

    override suspend fun setHideNsfwBoards(enabled: Boolean) {
        update { copy(hideNsfwBoards = enabled) }
    }

    override suspend fun setHideTextOnlyThreads(enabled: Boolean) {
        update { copy(hideTextOnlyThreads = enabled) }
    }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        update { copy(themeMode = mode) }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        update { copy(dynamicColor = enabled) }
    }

    override suspend fun setAmoled(enabled: Boolean) {
        update { copy(amoled = enabled) }
    }

    override suspend fun setFontScale(scale: Float) {
        update { copy(fontScale = scale) }
    }

    override suspend fun setThumbnailSize(size: ThumbnailSize) {
        update { copy(thumbnailSize = size) }
    }

    override suspend fun setAutoplayVideos(enabled: Boolean) {
        update { copy(autoplayVideos = enabled) }
    }

    override suspend fun setMuteByDefault(enabled: Boolean) {
        update { copy(muteByDefault = enabled) }
    }

    override suspend fun setPreloadImages(enabled: Boolean) {
        update { copy(preloadImages = enabled) }
    }

    override suspend fun setPreloadOption(option: PreloadOption) {
        update { copy(preloadOption = option) }
    }

    override suspend fun setPreloadThrottleMode(mode: PreloadThrottleMode) {
        update { copy(preloadThrottleMode = mode) }
    }

    override suspend fun setFeedThreadLimit(limit: FeedThreadLimit) {
        update { copy(feedThreadLimit = limit) }
    }

    override suspend fun setDownloadFolderUri(uri: String) {
        update { copy(downloadFolderUri = uri) }
    }

    override suspend fun setDohEnabled(enabled: Boolean) {
        update { copy(dohEnabled = enabled) }
    }

    override suspend fun setDohProvider(provider: DohProvider) {
        update { copy(dohProvider = provider) }
    }

    override suspend fun setBiometricLockEnabled(enabled: Boolean) {
        update { copy(biometricLockEnabled = enabled) }
    }

    override suspend fun setSaveRecentSearches(enabled: Boolean) {
        update { copy(saveRecentSearches = enabled) }
    }

    override suspend fun setUserAgent(userAgent: String) {
        update { copy(userAgent = userAgent) }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        update { copy(onboardingCompleted = completed) }
    }

    override suspend fun setActiveProviderId(id: ProviderId) {
        update { copy(activeProviderId = id.value) }
    }

    override suspend fun setColorTheme(theme: ColorTheme) {
        update { copy(colorTheme = theme) }
    }

    override suspend fun setAppIconVariant(variant: AppIconVariant) {
        update { copy(appIconVariant = variant) }
    }

    private fun update(block: AppSettings.() -> AppSettings) {
        state.update { it.block() }
    }
}
