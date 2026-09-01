package com.orbin.minimal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscribableBoard(
    val board: Board,
    val isSubscribed: Boolean,
)

data class MinimalBoardsUiState(
    val boards: ImmutableList<SubscribableBoard> = persistentListOf(),
    val isRefreshing: Boolean = true,
    val refreshError: String? = null,
    val subscriptionError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MinimalBoardsViewModel
    @Inject
    constructor(
        providerRegistry: ProviderRegistry,
        observeActiveProvider: ObserveActiveProviderUseCase,
        private val boardRepository: BoardRepository,
        private val boardPreferencesRepository: BoardPreferencesRepository,
    ) : ViewModel() {
        private val activeProvider: StateFlow<ImageBoardProvider> =
            observeActiveProvider()
                .stateIn(viewModelScope, SharingStarted.Eagerly, providerRegistry.default())

        private val sortedBoards =
            activeProvider.flatMapLatest { provider ->
                boardRepository
                    .observeBoards(provider.metadata.id)
                    .map { boards -> boards.sortedBy { it.id.value } }
            }

        private val subscribedBoards =
            activeProvider.flatMapLatest { provider ->
                boardPreferencesRepository.observeSubscribedBoards(provider.metadata.id)
            }

        private val content =
            combine(sortedBoards, subscribedBoards) { boards, subscribed ->
                boards
                    .map { board -> SubscribableBoard(board, board.id in subscribed) }
                    .toImmutableList()
            }

        private val refreshingState = MutableStateFlow(true)
        private val refreshErrorState = MutableStateFlow<String?>(null)
        private val subscriptionErrorState = MutableStateFlow<String?>(null)

        val uiState: StateFlow<MinimalBoardsUiState> =
            combine(
                content,
                refreshingState,
                refreshErrorState,
                subscriptionErrorState,
            ) { boards, refreshing, refreshError, subscriptionError ->
                MinimalBoardsUiState(
                    boards = boards,
                    isRefreshing = refreshing,
                    refreshError = refreshError,
                    subscriptionError = subscriptionError,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                MinimalBoardsUiState(),
            )

        /** Null until the active provider's subscription flow has emitted at least once. */
        val hasSubscriptions: StateFlow<Boolean?> =
            subscribedBoards
                .map<Set<BoardId>, Boolean?> { it.isNotEmpty() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        private var refreshJob: Job? = null

        init {
            activeProvider
                .onEach { provider -> refresh(provider.metadata.id) }
                .launchIn(viewModelScope)
        }

        fun setSubscribed(
            board: BoardId,
            subscribed: Boolean,
        ) {
            val provider = activeProvider.value.metadata.id
            viewModelScope.launch {
                subscriptionErrorState.value = null
                try {
                    boardPreferencesRepository.setSubscribedBoard(provider, board, subscribed)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    Log.w(TAG, "Could not update board subscription", error)
                    subscriptionErrorState.value = error.message.orEmpty().ifBlank { SUBSCRIPTION_ERROR }
                }
            }
        }

        fun refresh() {
            refresh(activeProvider.value.metadata.id)
        }

        private fun refresh(provider: ProviderId) {
            refreshJob?.cancel()
            refreshJob =
                viewModelScope.launch {
                    refreshingState.value = true
                    refreshErrorState.value = null
                    try {
                        when (val result = boardRepository.refreshBoards(provider)) {
                            is OrbinResult.Success -> Unit
                            is OrbinResult.Failure -> {
                                if (activeProvider.value.metadata.id == provider) {
                                    refreshErrorState.value = result.error.message
                                }
                            }
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Throwable) {
                        Log.w(TAG, "Could not refresh the board list", error)
                        if (activeProvider.value.metadata.id == provider) {
                            refreshErrorState.value = error.message.orEmpty().ifBlank { REFRESH_ERROR }
                        }
                    } finally {
                        if (activeProvider.value.metadata.id == provider) {
                            refreshingState.value = false
                        }
                    }
                }
        }

        private companion object {
            const val TAG = "MinimalBoardsViewModel"
            const val STOP_TIMEOUT_MS = 5_000L
            const val REFRESH_ERROR = "Could not load the board list"
            const val SUBSCRIPTION_ERROR = "Could not update that subscription"
        }
    }
