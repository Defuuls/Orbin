package com.orbin.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.OrbinResult
import com.orbin.domain.repository.BoardRepository
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Loads the board list for the default provider. Errors surface as a retryable [HomeUiState.Error]
 * rather than being swallowed, so connectivity problems are visible and recoverable.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val registry: ProviderRegistry,
        private val boardRepository: BoardRepository,
    ) : ViewModel() {
        private val provider = registry.default()

        /** Provider id passed along when navigating into a board. */
        val providerId: String = provider.metadata.id.value

        private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.value = HomeUiState.Loading
                _uiState.value =
                    when (val result = boardRepository.refreshBoards(provider.metadata.id)) {
                        is OrbinResult.Success ->
                            HomeUiState.Success(provider.metadata.displayName, result.data.toImmutableList())
                        is OrbinResult.Failure -> HomeUiState.Error(result.error.message)
                    }
            }
        }
    }
