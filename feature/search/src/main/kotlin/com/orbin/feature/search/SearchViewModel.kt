package com.orbin.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.fold
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.SearchContentType
import com.orbin.core.model.SearchFilters
import com.orbin.core.model.SearchQuery
import com.orbin.core.model.SearchResult
import com.orbin.core.model.SearchScope
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.SearchRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
        private val boardRepository: BoardRepository,
        boardPreferencesRepository: BoardPreferencesRepository,
        settingsRepository: SettingsRepository,
        registry: ProviderRegistry,
    ) : ViewModel() {
        private val providerId = registry.default().metadata.id

        val recentQueries: StateFlow<ImmutableList<String>> =
            searchRepository
                .observeRecentQueries()
                .map { it.toImmutableList() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), persistentListOf())

        val subscribedBoards: StateFlow<ImmutableList<Board>> =
            combine(
                boardRepository.observeBoards(providerId),
                boardPreferencesRepository.observeSubscribedBoards(providerId),
            ) { boards, subscribedIds ->
                boards
                    .filter { it.id in subscribedIds }
                    .sortedBy { it.id.value }
                    .toImmutableList()
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), persistentListOf())

        val saveRecentSearches: StateFlow<Boolean> =
            settingsRepository.settings
                .map { it.saveRecentSearches }
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)

        private val _state = kotlinx.coroutines.flow.MutableStateFlow<SearchUiState>(SearchUiState.Idle)
        val state: StateFlow<SearchUiState> = _state

        init {
            viewModelScope.launch { boardRepository.refreshBoards(providerId) }
        }

        fun search(
            text: String,
            board: String,
            contentTypes: Set<SearchContentType> = emptySet(),
        ) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                _state.value = SearchUiState.Loading
                if (saveRecentSearches.value) searchRepository.recordQuery(trimmed)
                val boardsToSearch =
                    board
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let { listOf(BoardId(it)) }
                        ?: subscribedBoards.value.map { it.id }

                if (boardsToSearch.isEmpty()) {
                    _state.value = SearchUiState.Error("Subscribe to boards before searching")
                    return@launch
                }

                val results = mutableListOf<SearchResult>()
                for (boardId in boardsToSearch) {
                    val query = buildQuery(trimmed, boardId, contentTypes)
                    val result = searchRepository.search(query)
                    var shouldStop = false
                    result.fold(
                        onSuccess = { results += it },
                        onFailure = {
                            _state.value = SearchUiState.Error(it.message)
                            shouldStop = true
                        },
                    )
                    if (shouldStop) return@launch
                }

                _state.value = SearchUiState.Results(results.toImmutableList())
            }
        }

        fun clearRecents() = viewModelScope.launch { searchRepository.clearRecentQueries() }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }

        private fun buildQuery(
            text: String,
            board: BoardId,
            contentTypes: Set<SearchContentType>,
        ): SearchQuery =
            SearchQuery(
                provider = providerId,
                text = text,
                scope = SearchScope.BOARD_CATALOG,
                board = board,
                filters =
                    SearchFilters(
                        mediaOnly =
                            contentTypes.any {
                                it == SearchContentType.IMAGE ||
                                    it == SearchContentType.VIDEO ||
                                    it == SearchContentType.AUDIO
                            },
                        contentTypes = contentTypes,
                    ),
            )
    }
