package com.orbin.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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

/**
 * Drives first run: loads the selected provider's boards to follow and persists the "onboarding
 * completed" flag when the reader starts browsing. Reuses the same repositories as home/settings.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val registry: ProviderRegistry,
        private val boardRepository: BoardRepository,
        private val boardPreferencesRepository: BoardPreferencesRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val _selectedProvider = MutableStateFlow<ImageBoardProvider>(registry.default())
        val selectedProvider: StateFlow<ImageBoardProvider> = _selectedProvider.asStateFlow()

        val providers: ImmutableList<ImageBoardProvider> = registry.all().toImmutableList()

        private val activeProvider: StateFlow<ImageBoardProvider> =
            selectedProvider

        private val _boards = MutableStateFlow<OnboardingBoardsState>(OnboardingBoardsState.Loading)
        val boards: StateFlow<OnboardingBoardsState> = _boards.asStateFlow()

        val subscribedBoardIds: StateFlow<Set<String>> =
            activeProvider
                .flatMapLatest { provider -> boardPreferencesRepository.observeSubscribedBoards(provider.metadata.id) }
                .map { ids -> ids.map { it.value }.toSet() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        init {
            activeProvider.onEach { loadBoards() }.launchIn(viewModelScope)
        }

        private var loadJob: Job? = null

        fun loadBoards() {
            loadJob?.cancel()
            val targetProviderId = activeProvider.value.metadata.id
            loadJob =
                viewModelScope.launch {
                    _boards.value = OnboardingBoardsState.Loading
                    val result = boardRepository.refreshBoards(targetProviderId)
                    _boards.value =
                        when (result) {
                            is OrbinResult.Success -> OnboardingBoardsState.Success(result.data.toImmutableList())
                            is OrbinResult.Failure -> OnboardingBoardsState.Error(result.error.message)
                        }
                }
        }

        fun setSubscribed(
            boardId: String,
            subscribed: Boolean,
        ) = update {
            boardPreferencesRepository.setSubscribedBoard(
                activeProvider.value.metadata.id,
                BoardId(boardId),
                subscribed,
            )
        }

        fun setSelectedProvider(provider: ImageBoardProvider) {
            _selectedProvider.value = provider
        }

        /** Persist that setup is done so the wizard never auto-shows again, and lock in the selected provider. */
        fun complete() =
            update {
                settingsRepository.setActiveProviderId(_selectedProvider.value.metadata.id)
                settingsRepository.setOnboardingCompleted(true)
            }

        private fun update(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
