package com.orbin.feature.thread

import androidx.compose.runtime.Immutable
import com.orbin.core.model.Thread

/** Immutable UI state for the thread viewer. */
@Immutable
sealed interface ThreadUiState {
    data object Loading : ThreadUiState

    data class Success(
        val thread: Thread,
        /**
         * True when the live thread could not be fetched and this is the reader's saved copy.
         * The UI says so: a saved thread reads as plain text and stops at the moment it was saved.
         */
        val fromSavedCopy: Boolean = false,
    ) : ThreadUiState

    data class Error(
        val message: String,
    ) : ThreadUiState
}
