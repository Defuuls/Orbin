package com.orbin.app.command

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.lock.AppLockController
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.feature.settings.settingsSearchIndex
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Everything the command surface can reach, and the filtering that narrows it.
 *
 * The query itself remains immediate so the text field never lags behind typing. Only the larger
 * catalogue match is debounced and moved to Default; that keeps rapid typing from repeatedly
 * walking boards, history and settings on the composition thread.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CommandViewModel
    @Inject
    constructor(
        registry: ProviderRegistry,
        observeActiveProvider: ObserveActiveProviderUseCase,
        boardRepository: BoardRepository,
        boardPreferencesRepository: BoardPreferencesRepository,
        historyRepository: HistoryRepository,
        private val appLockController: AppLockController,
    ) : ViewModel() {
        private val query = MutableStateFlow("")

        private val activeProvider: StateFlow<ImageBoardProvider> =
            observeActiveProvider()
                .stateIn(viewModelScope, SharingStarted.Eagerly, registry.default())

        private val catalogue: StateFlow<List<CommandTarget>> =
            activeProvider
                .flatMapLatest { provider ->
                    combine(
                        boardRepository.observeBoards(provider.metadata.id),
                        boardPreferencesRepository.observeSubscribedBoards(provider.metadata.id),
                        historyRepository.observeHistory(),
                    ) { boards, subscribed, history ->
                        buildCatalogue(provider.metadata.id.value, boards, subscribed, history)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), staticTargets())

        private val filteredResults =
            combine(query.debounce(COMMAND_DEBOUNCE_MS), catalogue) { text, targets ->
                filterCommands(targets, text)
            }.flowOn(Dispatchers.Default)

        val state: StateFlow<CommandUiState> =
            combine(query, filteredResults) { text, results ->
                CommandUiState(query = text, results = results)
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                CommandUiState("", filterCommands(staticTargets(), "")),
            )

        fun onQueryChange(text: String) {
            query.value = text
        }

        /** Cleared on dismiss so reopening starts fresh rather than resuming a stale search. */
        fun reset() {
            query.value = ""
        }

        fun lockNow() {
            appLockController.requestLock()
        }

        private fun buildCatalogue(
            providerId: String,
            boards: List<Board>,
            subscribed: Set<BoardId>,
            history: List<HistoryEntry>,
        ): List<CommandTarget> =
            buildList {
                addAll(staticTargets())
                history.take(RECENT_THREADS).forEach { entry ->
                    add(
                        CommandTarget.OpenThread(
                            label = entry.title,
                            hint = "/${entry.key.board.value}/",
                            provider = entry.key.provider.value,
                            board = entry.key.board.value,
                            thread = entry.key.thread.value,
                        ),
                    )
                }
                boards
                    .filterNot { board -> board.isPermanentlyFiltered() }
                    .sortedBy { board -> board.id.value }
                    .forEach { board ->
                        add(
                            CommandTarget.OpenBoard(
                                label = "/${board.id.value}/",
                                hint =
                                    listOfNotNull(
                                        board.title.takeIf { it.isNotBlank() },
                                        "subscribed".takeIf { board.id in subscribed },
                                    ).joinToString("  ·  "),
                                provider = providerId,
                                board = board.id.value,
                                title = board.title,
                            ),
                        )
                    }
                settingsSearchIndex.forEach { entry ->
                    add(CommandTarget.OpenSetting(entry.label, entry.group, entry.id))
                }
            }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val RECENT_THREADS = 8
            const val COMMAND_DEBOUNCE_MS = 120L
        }
    }

data class CommandUiState(
    val query: String,
    val results: List<CommandTarget>,
)
