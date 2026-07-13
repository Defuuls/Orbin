package com.orbin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
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

/** UI state for the board subscriptions screen. */
sealed interface SubscriptionsUiState {
    data object Loading : SubscriptionsUiState

    data class Error(
        val message: String,
    ) : SubscriptionsUiState

    data class Success(
        val boards: ImmutableList<Board>,
    ) : SubscriptionsUiState
}

/**
 * Backs the Subscriptions settings screen: loads the active provider's boards and exposes the
 * subscribed set plus a toggle action. Mirrors the board-preferences wiring used on home so the
 * subscribe controls can live under Settings rather than the board-setup overlay.
 */
@HiltViewModel
class SubscriptionsViewModel
    @Inject
    constructor(
        registry: ProviderRegistry,
        observeActiveProvider: ObserveActiveProviderUseCase,
        private val boardRepository: BoardRepository,
        private val boardPreferencesRepository: BoardPreferencesRepository,
    ) : ViewModel() {
        private val activeProvider: StateFlow<ImageBoardProvider> =
            observeActiveProvider()
                .stateIn(viewModelScope, SharingStarted.Eagerly, registry.default())

        private val _uiState = MutableStateFlow<SubscriptionsUiState>(SubscriptionsUiState.Loading)
        val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

        val subscribedBoardIds: StateFlow<Set<String>> =
            activeProvider
                .flatMapLatest { provider -> boardPreferencesRepository.observeSubscribedBoards(provider.metadata.id) }
                .map { boards -> boards.map { it.value }.toSet() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        init {
            activeProvider.onEach { load() }.launchIn(viewModelScope)
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.value = SubscriptionsUiState.Loading
                _uiState.value =
                    when (val result = boardRepository.refreshBoards(activeProvider.value.metadata.id)) {
                        is OrbinResult.Success ->
                            SubscriptionsUiState.Success(result.data.toImmutableList())
                        is OrbinResult.Failure -> SubscriptionsUiState.Error(result.error.message)
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
