package com.orbin.feature.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.fold
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.domain.usecase.ObserveThreadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Observes a thread and maps the result to [ThreadUiState] for the viewer. */
@HiltViewModel
class ThreadViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        observeThread: ObserveThreadUseCase,
    ) : ViewModel() {
        val title: String = savedStateHandle.get<String>("title").orEmpty()

        private val provider = ProviderId(savedStateHandle.get<String>("provider").orEmpty())
        private val board = BoardId(savedStateHandle.get<String>("board").orEmpty())
        private val threadId = ThreadId(savedStateHandle.get<Long>("thread") ?: 0L)

        val uiState: StateFlow<ThreadUiState> =
            observeThread(provider, board, threadId)
                .map { result ->
                    result.fold(
                        onSuccess = { ThreadUiState.Success(it) },
                        onFailure = { ThreadUiState.Error(it.message) },
                    )
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ThreadUiState.Loading)

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
