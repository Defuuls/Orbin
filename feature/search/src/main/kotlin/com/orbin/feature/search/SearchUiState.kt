package com.orbin.feature.search

import androidx.compose.runtime.Immutable
import com.orbin.core.model.SearchResult
import kotlinx.collections.immutable.ImmutableList

/** Immutable UI state for the search screen. */
@Immutable
sealed interface SearchUiState {
    data object Idle : SearchUiState

    data object Loading : SearchUiState

    data class Results(
        val results: ImmutableList<SearchResult>,
    ) : SearchUiState

    data class Error(
        val message: String,
    ) : SearchUiState
}
