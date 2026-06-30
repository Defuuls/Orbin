package com.orbin.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.ProviderId
import com.orbin.core.model.hiddenTagTokens
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

sealed interface SubscribedFeedUiState {
    data object Loading : SubscribedFeedUiState

    data class Error(
        val message: String,
    ) : SubscribedFeedUiState

    data class Success(
        val boards: ImmutableList<SubscribedBoardFeed>,
    ) : SubscribedFeedUiState
}

data class SubscribedBoardFeed(
    val board: Board,
    val threads: ImmutableList<CatalogThread>,
)

@HiltViewModel
class SubscribedFeedViewModel
    @Inject
    constructor(
        registry: ProviderRegistry,
        private val boardRepository: BoardRepository,
        boardPreferencesRepository: BoardPreferencesRepository,
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val provider = registry.default()
        val providerId: String = provider.metadata.id.value

        private val refreshRequests = MutableStateFlow(0)

        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings.Default)

        val uiState: StateFlow<SubscribedFeedUiState> =
            combine(
                boardRepository.observeBoards(provider.metadata.id),
                boardPreferencesRepository.observeSubscribedBoards(provider.metadata.id),
                settings,
                refreshRequests,
            ) { boards, subscribedIds, settings, _ ->
                loadSubscribedFeeds(boards, subscribedIds, settings)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SubscribedFeedUiState.Loading)

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                boardRepository.refreshBoards(provider.metadata.id)
                refreshRequests.value += 1
            }
        }

        private suspend fun loadSubscribedFeeds(
            boards: List<Board>,
            subscribedIds: Set<BoardId>,
            settings: AppSettings,
        ): SubscribedFeedUiState {
            if (subscribedIds.isEmpty()) {
                return SubscribedFeedUiState.Success(emptyList<SubscribedBoardFeed>().toImmutableList())
            }

            val subscribedBoards =
                boards
                    .filter { it.id in subscribedIds }
                    .filterNot { board -> settings.hideNsfwBoards && board.isNsfw }
                    .sortedBy { it.id.value }

            if (subscribedBoards.isEmpty()) {
                return SubscribedFeedUiState.Success(emptyList<SubscribedBoardFeed>().toImmutableList())
            }

            return try {
                val requestLimit = Semaphore(MAX_CONCURRENT_BOARD_LOADS)
                val feeds =
                    kotlinx.coroutines.coroutineScope {
                        subscribedBoards
                            .map { board ->
                                async {
                                    val threads = requestLimit.withPermit { loadBoardThreads(board, settings) }
                                    SubscribedBoardFeed(board, threads)
                                }
                            }.map { it.await() }
                    }
                SubscribedFeedUiState.Success(feeds.toImmutableList())
            } catch (e: ProviderException) {
                SubscribedFeedUiState.Error(e.message ?: "Unable to load subscribed boards")
            }
        }

        private suspend fun loadBoardThreads(
            board: Board,
            settings: AppSettings,
        ): ImmutableList<CatalogThread> {
            val catalog = provider.getCatalog(CatalogRequest(ProviderId(providerId), board.id))
            return (settings.feedThreadLimit.count?.let(catalog::take) ?: catalog)
                .filterNot { thread -> thread.matchesAny(settings.hiddenTagTokens()) }
                .filterNot { thread -> settings.hideTextOnlyThreads && thread.originalPost.attachments.isEmpty() }
                .toImmutableList()
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val MAX_CONCURRENT_BOARD_LOADS = 4
        }
    }

private fun CatalogThread.matchesAny(tokens: Set<String>): Boolean {
    if (tokens.isEmpty()) return false
    val haystack = listOfNotNull(originalPost.subject, originalPost.comment).joinToString(" ").lowercase()
    return tokens.any(haystack::contains)
}
