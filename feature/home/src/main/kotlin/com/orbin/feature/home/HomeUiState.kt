package com.orbin.feature.home

import androidx.compose.runtime.Immutable
import com.orbin.core.model.Board
import kotlinx.collections.immutable.ImmutableList

/** Immutable UI state for the home (board list) screen. */
@Immutable
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Success(
        val providerName: String,
        val boards: ImmutableList<Board>,
    ) : HomeUiState

    data class Error(
        val message: String,
    ) : HomeUiState
}
