package com.orbin.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
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
 * Drives the first-run setup wizard: loads the active provider's boards for the subscribe step,
 * exposes the live [AppSettings] for the preference steps, and persists the "onboarding completed"
 * flag when the user finishes. Reuses the same repositories as home/settings.
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val registry: ProviderRegistry,
        observeActiveProvider: ObserveActiveProviderUseCase,
        private val boardRepository: BoardRepository,
        private val boardPreferencesRepository: BoardPreferencesRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val activeProvider: StateFlow<ImageBoardProvider> =
            observeActiveProvider()
                .stateIn(viewModelScope, SharingStarted.Eagerly, registry.default())

        private val _boards = MutableStateFlow<OnboardingBoardsState>(OnboardingBoardsState.Loading)
        val boards: StateFlow<OnboardingBoardsState> = _boards.asStateFlow()

        val subscribedBoardIds: StateFlow<Set<String>> =
            activeProvider
                .flatMapLatest { provider -> boardPreferencesRepository.observeSubscribedBoards(provider.metadata.id) }
                .map { ids -> ids.map { it.value }.toSet() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        val favoriteBoardIds: StateFlow<Set<String>> =
            activeProvider
                .flatMapLatest { provider -> boardPreferencesRepository.observeFavoriteBoards(provider.metadata.id) }
                .map { ids -> ids.map { it.value }.toSet() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings.Default)

        init {
            activeProvider.onEach { loadBoards() }.launchIn(viewModelScope)
        }

        fun loadBoards() {
            viewModelScope.launch {
                _boards.value = OnboardingBoardsState.Loading
                _boards.value =
                    when (val result = boardRepository.refreshBoards(activeProvider.value.metadata.id)) {
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

        fun setFavorite(
            boardId: String,
            favorite: Boolean,
        ) = update {
            boardPreferencesRepository.setFavoriteBoard(activeProvider.value.metadata.id, BoardId(boardId), favorite)
        }

        fun setThemeMode(mode: AppThemeMode) = update { settingsRepository.setThemeMode(mode) }

        fun setDynamicColor(enabled: Boolean) = update { settingsRepository.setDynamicColor(enabled) }

        fun setAmoled(enabled: Boolean) = update { settingsRepository.setAmoled(enabled) }

        fun setAutoplay(enabled: Boolean) = update { settingsRepository.setAutoplayVideos(enabled) }

        fun setMute(enabled: Boolean) = update { settingsRepository.setMuteByDefault(enabled) }

        fun setPreload(enabled: Boolean) = update { settingsRepository.setPreloadImages(enabled) }

        fun setDoh(enabled: Boolean) = update { settingsRepository.setDohEnabled(enabled) }

        fun setBiometricLock(enabled: Boolean) = update { settingsRepository.setBiometricLockEnabled(enabled) }

        fun setSaveRecentSearches(enabled: Boolean) = update { settingsRepository.setSaveRecentSearches(enabled) }

        /** Persist that setup is done so the wizard never auto-shows again, and lock in the active provider. */
        fun complete() =
            update {
                settingsRepository.setActiveProviderId(activeProvider.value.metadata.id)
                settingsRepository.setOnboardingCompleted(true)
            }

        private fun update(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
