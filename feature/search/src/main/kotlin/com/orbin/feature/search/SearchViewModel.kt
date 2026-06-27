package com.orbin.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.fold
import com.orbin.core.model.BoardId
import com.orbin.core.model.SearchQuery
import com.orbin.core.model.SearchScope
import com.orbin.domain.repository.SearchRepository
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Drives the search screen: client-side catalog search plus persisted recent queries. */
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val searchRepository: SearchRepository,
        registry: ProviderRegistry,
    ) : ViewModel() {
        private val providerId = registry.default().metadata.id

        val recentQueries: StateFlow<ImmutableList<String>> =
            searchRepository
                .observeRecentQueries()
                .map { it.toImmutableList() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), persistentListOf())

        private val _state = kotlinx.coroutines.flow.MutableStateFlow<SearchUiState>(SearchUiState.Idle)
        val state: StateFlow<SearchUiState> = _state

        fun search(
            text: String,
            board: String,
        ) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                _state.value = SearchUiState.Loading
                searchRepository.recordQuery(trimmed)
                val query =
                    SearchQuery(
                        provider = providerId,
                        text = trimmed,
                        scope = SearchScope.BOARD_CATALOG,
                        board = board.trim().takeIf { it.isNotBlank() }?.let(::BoardId),
                    )
                _state.value =
                    searchRepository.search(query).fold(
                        onSuccess = { SearchUiState.Results(it.toImmutableList()) },
                        onFailure = { SearchUiState.Error(it.message) },
                    )
            }
        }

        fun clearRecents() = viewModelScope.launch { searchRepository.clearRecentQueries() }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
