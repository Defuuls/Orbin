package com.orbin.feature.thread

import androidx.compose.runtime.Immutable
import com.orbin.core.common.result.DataError
import com.orbin.core.model.Thread
import com.orbin.core.reader.ThreadPresentationIndex

/** Immutable UI state for the thread viewer. */
@Immutable
sealed interface ThreadUiState {
    data object Loading : ThreadUiState

    data class Success(
        val thread: Thread,
        val presentation: ThreadPresentationIndex,
        /**
         * True when the live thread could not be fetched and this is the reader's saved copy.
         * The UI says so: a saved thread reads as plain text and stops at the moment it was saved.
         */
        val fromSavedCopy: Boolean = false,
        val refreshFailure: DataError? = null,
    ) : ThreadUiState

    data class Error(
        val message: String,
        val cause: DataError? = null,
    ) : ThreadUiState

    /**
     * The thread's opening post is caught by the permanent content filter, so the thread is not
     * rendered at all — not even the OP, which the hidden-keyword filter would have left in place.
     *
     * Distinct from [Error] because nothing went wrong and there is nothing to retry: the reader
     * gets an explanation instead of a failure they would keep tapping at.
     */
    data object Blocked : ThreadUiState
}
