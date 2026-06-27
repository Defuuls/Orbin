package com.orbin.feature.thread

import androidx.compose.runtime.Immutable
import com.orbin.core.model.Thread

/** Immutable UI state for the thread viewer. */
@Immutable
sealed interface ThreadUiState {
    data object Loading : ThreadUiState

    data class Success(
        val thread: Thread,
    ) : ThreadUiState

    data class Error(
        val message: String,
    ) : ThreadUiState
}
