package com.orbin.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.DataError
import com.orbin.core.common.result.fold
import com.orbin.core.model.Board
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SearchQuery
import com.orbin.core.model.SearchScope
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.SearchRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.search.searchEachBoard
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives one query across favorite and followed boards on every registered source.
 *
 * There is nothing to configure. NSFW boards are skipped when Settings hides them, the same as the
 * feed does, and nothing about a search is stored.
 */
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val searchRepository: SearchRepository,
        private val boardRepository: BoardRepository,
        boardPreferencesRepository: BoardPreferencesRepository,
        private val settingsRepository: SettingsRepository,
        registry: ProviderRegistry,
        @Suppress("UNUSED_PARAMETER") observeActiveProvider: ObserveActiveProviderUseCase,
    ) : ViewModel() {
        private val providers = registry.all()

        // A board code alone is ambiguous: both sites can have /g/. Keep the provider attached
        // until the request is issued, independent of the source selected in the Boards tab.
        private val searchBoards: StateFlow<List<SearchBoard>> =
            combine(
                providers.map { provider ->
                    combine(
                        boardRepository.observeBoards(provider.metadata.id),
                        boardPreferencesRepository.observeSubscribedBoards(provider.metadata.id),
                        boardPreferencesRepository.observeFavoriteBoards(provider.metadata.id),
                        settingsRepository.settings,
                    ) { boards, subscribed, favorites, settings ->
                        boards
                            .filter { it.id in subscribed || it.id in favorites }
                            .filterNot { settings.hideNsfwBoards && it.isNsfw }
                            .filterNot { it.isPermanentlyFiltered() }
                            .sortedBy { it.id.value }
                            .map { SearchBoard(provider.metadata.id, it) }
                    }
                },
            ) { perSite -> perSite.flatMap { it } }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        /** Favorites and existing subscriptions across all sites, with the same safety filters as Home. */
        val searchableBoards: StateFlow<ImmutableList<Board>> =
            searchBoards
                .map { entries -> entries.map { it.board }.toImmutableList() }
                .stateIn(viewModelScope, SharingStarted.Eagerly, persistentListOf())

        private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
        val state: StateFlow<SearchUiState> = _state.asStateFlow()

        init {
            providers.forEach { provider ->
                viewModelScope.launch { boardRepository.refreshBoards(provider.metadata.id) }
            }
        }

        private var searchJob: Job? = null

        fun search(text: String) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return
            searchJob?.cancel()
            searchJob =
                viewModelScope.launch {
                    _state.value = SearchUiState.Loading
                    val boards = searchBoards.value
                    if (boards.isEmpty()) {
                        _state.value = SearchUiState.Error("Favorite a few boards to search them")
                        return@launch
                    }
                    // Boards are searched a few at a time; one that fails leaves the others' results.
                    val outcome =
                        searchEachBoard(boards) { board ->
                            searchRepository
                                .search(SearchQuery(board.provider, trimmed, SearchScope.BOARD_CATALOG, board.board.id))
                                .fold(onSuccess = { it }, onFailure = { throw BoardSearchFailed(it) })
                        }
                    if (outcome.allFailed) {
                        _state.value = SearchUiState.Error(outcome.firstError?.message ?: "Search failed")
                        return@launch
                    }
                    if (settingsRepository.settings.first().saveSearchHistory) {
                        searchRepository.recordQuery(trimmed)
                    }
                    val results = outcome.results
                    _state.value = SearchUiState.Results(results.toImmutableList())
                }
        }
    }

/** A source-qualified search target; source identity must survive identical board codes. */
private data class SearchBoard(
    val provider: ProviderId,
    val board: Board,
)

/** A board's search failure, carried through [searchEachBoard] with its readable message. */
private class BoardSearchFailed(
    error: DataError,
) : Exception(error.message, error.cause)
