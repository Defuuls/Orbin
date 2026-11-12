package com.orbin.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.matchesFilterTokens
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/** Board list state for the onboarding "pick boards" step. */
sealed interface OnboardingBoardsState {
    data object Loading : OnboardingBoardsState

    data class Error(
        val message: String,
    ) : OnboardingBoardsState

    data class Success(
        val boards: ImmutableList<Board>,
    ) : OnboardingBoardsState
}

/** A successful follow write is required before first run can be completed. */
data class OnboardingSetupState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val completed: Boolean = false,
)

/**
 * Drives first run: loads the selected provider's boards to follow and persists the "onboarding
 * completed" flag when the reader starts browsing. Reuses the same repositories as home/settings.
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        registry: ProviderRegistry,
        private val boardRepository: BoardRepository,
        private val boardPreferencesRepository: BoardPreferencesRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val _selectedProvider = MutableStateFlow<ImageBoardProvider>(registry.default())
        val selectedProvider: StateFlow<ImageBoardProvider> = _selectedProvider.asStateFlow()

        val providers: ImmutableList<ImageBoardProvider> = registry.all().toImmutableList()

        private val _boards = MutableStateFlow<OnboardingBoardsState>(OnboardingBoardsState.Loading)
        val boards: StateFlow<OnboardingBoardsState> = _boards.asStateFlow()

        private val followedBoards: StateFlow<Map<ProviderId, Set<BoardId>>> =
            combine(
                providers.map { provider ->
                    combine(
                        boardPreferencesRepository.observeSubscribedBoards(provider.metadata.id),
                        boardPreferencesRepository.observeFavoriteBoards(provider.metadata.id),
                    ) { followed, favorite -> provider.metadata.id to (followed + favorite) }
                },
            ) { it.toMap() }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

        val subscribedBoardIds: StateFlow<Set<String>> =
            combine(selectedProvider, followedBoards) { provider, boards ->
                boards[provider.metadata.id].orEmpty().map { it.value }.toSet()
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

        private val eligibleBoards = MutableStateFlow<Map<ProviderId, Set<BoardId>>>(emptyMap())

        val totalFollowing: StateFlow<Int> =
            combine(followedBoards, eligibleBoards) { followed, eligible ->
                followed.entries.sumOf { (provider, boards) -> boards.count { it in eligible[provider].orEmpty() } }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

        private val _setupState = MutableStateFlow(OnboardingSetupState())
        val setupState: StateFlow<OnboardingSetupState> = _setupState.asStateFlow()

        private val writes = Mutex()
        private var pendingWrites = 0
        private var completing = false
        private var loadJob: Job? = null

        init {
            selectedProvider.onEach { loadBoards() }.launchIn(viewModelScope)
        }

        fun loadBoards() {
            loadJob?.cancel()
            val targetProviderId = selectedProvider.value.metadata.id
            _boards.value = OnboardingBoardsState.Loading
            loadJob =
                viewModelScope.launch {
                    try {
                        val result = boardRepository.refreshBoards(targetProviderId)
                        coroutineContext.ensureActive()
                        if (selectedProvider.value.metadata.id != targetProviderId) return@launch
                        _boards.value =
                            when (result) {
                                is OrbinResult.Success -> {
                                    val hideNsfw = settingsRepository.settings.first().hideNsfwBoards
                                    val visible =
                                        result.data
                                            .filterNot { board -> hideNsfw && board.isNsfw }
                                            .filterNot { board -> board.matchesFilterTokens(emptySet()) }
                                    eligibleBoards.update { current ->
                                        current + (targetProviderId to visible.mapTo(mutableSetOf()) { it.id })
                                    }
                                    OnboardingBoardsState.Success(
                                        visible.toImmutableList(),
                                    )
                                }
                                is OrbinResult.Failure -> OnboardingBoardsState.Error(result.error.message)
                            }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (selectedProvider.value.metadata.id == targetProviderId) {
                            _boards.value = OnboardingBoardsState.Error("Couldn't load boards. Try again.")
                        }
                    }
                }
        }

        fun setSubscribed(
            boardId: String,
            subscribed: Boolean,
        ) {
            if (completing || _setupState.value.completed) return
            val visibleBoards = (_boards.value as? OnboardingBoardsState.Success)?.boards ?: return
            if (visibleBoards.none { it.id.value == boardId }) return
            val provider = selectedProvider.value.metadata.id
            pendingWrites++
            _setupState.update { it.copy(isSaving = true, errorMessage = null) }
            viewModelScope.launch {
                try {
                    writes.withLock {
                        boardPreferencesRepository.setSubscribedBoard(provider, BoardId(boardId), subscribed)
                        if (!subscribed) boardPreferencesRepository.setFavoriteBoard(provider, BoardId(boardId), false)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _setupState.update { it.copy(errorMessage = "Couldn't save your followed boards. Try again.") }
                } finally {
                    pendingWrites--
                    _setupState.update { it.copy(isSaving = pendingWrites > 0) }
                }
            }
        }

        fun setSelectedProvider(provider: ImageBoardProvider) {
            if (completing || _setupState.value.completed || provider !in providers) return
            if (_selectedProvider.value.metadata.id == provider.metadata.id) return
            _boards.value = OnboardingBoardsState.Loading
            _selectedProvider.value = provider
        }

        /** Complete only after the followed boards and both settings writes have reached storage. */
        fun complete() {
            if (pendingWrites > 0 || completing || _setupState.value.completed) return
            completing = true
            _setupState.update { it.copy(isSaving = true, errorMessage = null) }
            val selected = selectedProvider.value.metadata.id
            viewModelScope.launch {
                try {
                    writes.withLock {
                        val persisted =
                            providers.associate { provider ->
                                val id = provider.metadata.id
                                val followed =
                                    boardPreferencesRepository.observeSubscribedBoards(id).first() +
                                        boardPreferencesRepository.observeFavoriteBoards(id).first()
                                id to followed.intersect(eligibleBoards.value[id].orEmpty())
                            }
                        val active =
                            selected.takeIf { persisted[it].orEmpty().isNotEmpty() }
                                ?: providers
                                    .firstOrNull {
                                        persisted[it.metadata.id].orEmpty().isNotEmpty()
                                    }?.metadata
                                    ?.id
                        if (active == null) {
                            _setupState.update {
                                it.copy(
                                    errorMessage = "Follow at least one board to start browsing.",
                                )
                            }
                            return@withLock
                        }
                        withContext(NonCancellable) {
                            settingsRepository.setActiveProviderId(active)
                            settingsRepository.setOnboardingCompleted(true)
                        }
                        _setupState.update { it.copy(completed = true) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _setupState.update { it.copy(errorMessage = "Couldn't finish setup. Try again.") }
                } finally {
                    completing = false
                    _setupState.update { it.copy(isSaving = false) }
                }
            }
        }
    }
