package com.orbin.minimal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
            viewModelScope.launch { boardRepository.refreshBoards(activeProvider.value.metadata.id) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
