package com.orbin.minimal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A board and whether the reader has subscribed to it. */
data class SubscribableBoard(
    val board: Board,
    val isSubscribed: Boolean,
)

/**
 * The board picker's state.
 *
 * This build has no settings screen, so subscribing has to live somewhere: this is it, and it is
 * the only thing here besides the feed and the reader. It writes to the same
 * [BoardPreferencesRepository] the full client uses — but into this app's own sandboxed database,
 * since the two APKs carry different applicationIds.
 */
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

        val boards: StateFlow<ImmutableList<SubscribableBoard>> =
            activeProvider
                .flatMapLatest { provider ->
                    combine(
                        boardRepository.observeBoards(provider.metadata.id),
                        boardPreferencesRepository.observeSubscribedBoards(provider.metadata.id),
                    ) { boards, subscribed ->
                        boards
                            .sortedBy { it.id.value }
                            .map { SubscribableBoard(it, it.id in subscribed) }
                            .toImmutableList()
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), persistentListOf())

        private val _isLoading = MutableStateFlow(false)

        /**
         * Whether a fetch is in flight.
         *
         * Without this the screen cannot tell "still loading" from "this provider has no boards":
         * both are an empty list, and it showed a spinner for both, so a failed or empty fetch left
         * a spinner turning forever with no way out.
         */
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        init {
            // Nothing else asked for the board list. Opening the picker on a fresh install left it
            // waiting on a fetch that was never going to be made.
            refresh()
        }

        fun setSubscribed(
            board: BoardId,
            subscribed: Boolean,
        ) {
            viewModelScope.launch {
                boardPreferencesRepository.setSubscribedBoard(
                    activeProvider.value.metadata.id,
                    board,
                    subscribed,
                )
            }
        }

        fun refresh() {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = null
                try {
                    boardRepository.refreshBoards(activeProvider.value.metadata.id)
                } catch (e: ProviderException) {
                    // A provider that cannot be reached is the common case worth reporting; the
                    // screen offers a retry rather than sitting on an empty list.
                    Log.w(TAG, "Could not refresh the board list", e)
                    // Empty rather than null when the exception says nothing: a failure with no
                    // message is still a failure, and null here would have the screen report an
                    // empty board list instead — the one thing it is not.
                    _errorMessage.value = e.message.orEmpty()
                } finally {
                    _isLoading.value = false
                }
            }
        }

        private companion object {
            const val TAG = "MinimalBoardsViewModel"
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
