package com.orbin.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.fold
import com.orbin.core.model.Board
import com.orbin.core.model.SearchQuery
import com.orbin.core.model.SearchResult
import com.orbin.core.model.SearchScope
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.SearchRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the search screen: one query, searched across the catalogs of every board you follow.
 *
 * There is nothing to configure. NSFW boards are skipped when Settings hides them, the same as the
 * feed does, and nothing about a search is stored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val searchRepository: SearchRepository,
        private val boardRepository: BoardRepository,
        boardPreferencesRepository: BoardPreferencesRepository,
        settingsRepository: SettingsRepository,
        registry: ProviderRegistry,
        observeActiveProvider: ObserveActiveProviderUseCase,
    ) : ViewModel() {
        private val activeProvider: StateFlow<ImageBoardProvider> =
            observeActiveProvider()
                .stateIn(viewModelScope, SharingStarted.Eagerly, registry.default())

        /** The boards a search covers: the ones you follow, less NSFW ones when those are hidden. */
        val searchableBoards: StateFlow<ImmutableList<Board>> =
            activeProvider
                .flatMapLatest { provider ->
                    combine(
                        boardRepository.observeBoards(provider.metadata.id),
                        boardPreferencesRepository.observeSubscribedBoards(provider.metadata.id),
                        settingsRepository.settings,
                    ) { boards, subscribedIds, settings ->
                        boards
                            .filter { it.id in subscribedIds }
                            .filterNot { settings.hideNsfwBoards && it.isNsfw }
                            .sortedBy { it.id.value }
                            .toImmutableList()
                    }
                }.stateIn(viewModelScope, SharingStarted.Eagerly, persistentListOf())

        private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
        val state: StateFlow<SearchUiState> = _state.asStateFlow()

        init {
            activeProvider
                .onEach { provider -> boardRepository.refreshBoards(provider.metadata.id) }
                .launchIn(viewModelScope)
        }

        fun search(text: String) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                _state.value = SearchUiState.Loading
                val boards = searchableBoards.value
                if (boards.isEmpty()) {
                    _state.value = SearchUiState.Error("Follow a few boards to search them")
                    return@launch
                }
                val results = mutableListOf<SearchResult>()
                for (board in boards) {
                    val query =
                        SearchQuery(
                            provider = activeProvider.value.metadata.id,
                            text = trimmed,
                            scope = SearchScope.BOARD_CATALOG,
                            board = board.id,
                        )
                    var failed = false
                    searchRepository.search(query).fold(
                        onSuccess = { results += it },
                        onFailure = {
                            _state.value = SearchUiState.Error(it.message)
                            failed = true
                        },
                    )
                    if (failed) return@launch
                }
                _state.value = SearchUiState.Results(results.toImmutableList())
            }
        }
    }
