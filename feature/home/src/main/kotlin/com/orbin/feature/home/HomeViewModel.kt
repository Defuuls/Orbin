package com.orbin.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import com.orbin.uinext.sourceLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Loads the board list for the active provider. Errors surface as a retryable [HomeUiState.Error]
 * rather than being swallowed, so connectivity problems are visible and recoverable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        registry: ProviderRegistry,
        observeActiveProvider: ObserveActiveProviderUseCase,
        private val boardRepository: BoardRepository,
        private val boardPreferencesRepository: BoardPreferencesRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val activeProvider: StateFlow<ImageBoardProvider> =
            observeActiveProvider()
                .stateIn(viewModelScope, SharingStarted.Eagerly, registry.default())

        internal val sites =
            registry.all().map {
                FeedSiteOption(
                    it.metadata.id.value,
                    sourceLabel(it.metadata.displayName),
                )
            }

        fun selectSite(id: String) {
            if (sites.none { it.id == id }) return
            viewModelScope.launch { settingsRepository.setActiveProviderId(ProviderId(id)) }
        }

        /** First-run subscriptions and older favorites are the same Following choice in Boards. */
        val followedBoardIds: StateFlow<Set<String>> =
            activeProvider
                .flatMapLatest { provider ->
                    combine(
                        boardPreferencesRepository.observeFavoriteBoards(provider.metadata.id),
                        boardPreferencesRepository.observeSubscribedBoards(provider.metadata.id),
                    ) { favorites, subscriptions -> favorites + subscriptions }
                }.map { boards -> boards.map { it.value }.toSet() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        private val followWrites = Mutex()

        fun setFollowed(
            boardId: String,
            followed: Boolean,
            sourceId: String = activeProvider.value.metadata.id.value,
        ) {
            val provider = ProviderId(sourceId)
            val board = BoardId(boardId)
            viewModelScope.launch {
                followWrites.withLock {
                    boardPreferencesRepository.setFavoriteBoard(provider, board, followed)
                    if (!followed) boardPreferencesRepository.setSubscribedBoard(provider, board, false)
                }
            }
        }

        /** Provider id passed along when navigating into a board. */
        val providerId: StateFlow<String> =
            activeProvider
                .map { it.metadata.id.value }
                .stateIn(viewModelScope, SharingStarted.Eagerly, activeProvider.value.metadata.id.value)

        private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        val subscribedBoardIds: StateFlow<Set<String>> =
            activeProvider
                .flatMapLatest { provider -> boardPreferencesRepository.observeSubscribedBoards(provider.metadata.id) }
                .map { boards -> boards.map { it.value }.toSet() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings.Default)

        private var loadJob: Job? = null

        init {
            activeProvider.onEach { load() }.launchIn(viewModelScope)
        }

        fun load() {
            loadJob?.cancel()
            val provider = activeProvider.value
            _uiState.value = HomeUiState.Loading
            loadJob =
                viewModelScope.launch {
                    _uiState.value =
                        when (val result = boardRepository.refreshBoards(provider.metadata.id)) {
                            is OrbinResult.Success ->
                                HomeUiState.Success(
                                    provider.metadata.displayName,
                                    result.data.toImmutableList(),
                                    provider.metadata.id.value,
                                )
                            is OrbinResult.Failure -> HomeUiState.Error(result.error.message)
                        }
                }
        }

        fun setSubscribed(
            boardId: String,
            subscribed: Boolean,
        ) {
            viewModelScope.launch {
                boardPreferencesRepository.setSubscribedBoard(
                    provider = activeProvider.value.metadata.id,
                    board = BoardId(boardId),
                    subscribed = subscribed,
                )
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
