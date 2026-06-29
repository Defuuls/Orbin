package com.orbin.feature.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.Board
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadKey
import com.orbin.domain.repository.ThreadRepository
import com.orbin.provider.api.ProviderRegistry
import com.orbin.provider.api.require
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GalleryBrowserUiState(
    val provider: ProviderId = ProviderId(""),
    val boards: ImmutableList<Board> = persistentListOf(),
    val selectedBoard: Board? = null,
    val threads: ImmutableList<CatalogThread> = persistentListOf(),
    val selectedThread: CatalogThread? = null,
    val media: ImmutableList<MediaAttachment> = persistentListOf(),
    val loadingBoards: Boolean = true,
    val loadingThreads: Boolean = false,
    val preloadingThread: Boolean = false,
    val progressMessage: String? = null,
    val progressValue: Float = 0f,
    val message: String? = null,
)

@HiltViewModel
class GalleryBrowserViewModel
    @Inject
    constructor(
        private val providerRegistry: ProviderRegistry,
        private val threadRepository: ThreadRepository,
    ) : ViewModel() {
        private val provider = providerRegistry.default()
        private val providerId = provider.metadata.id
        private var threadJob: Job? = null

        private val _uiState = MutableStateFlow(GalleryBrowserUiState(provider = providerId))
        val uiState: StateFlow<GalleryBrowserUiState> = _uiState.asStateFlow()

        init {
            loadBoards()
        }

        fun selectBoard(board: Board) {
            if (_uiState.value.selectedBoard?.id == board.id) return
            _uiState.update {
                it.copy(
                    selectedBoard = board,
                    threads = persistentListOf(),
                    selectedThread = null,
                    media = persistentListOf(),
                    loadingThreads = true,
                    message = null,
                )
            }
            loadThreads(board)
        }

        fun selectThread(thread: CatalogThread) {
            if (_uiState.value.selectedThread?.key == thread.key) return
            _uiState.update {
                it.copy(selectedThread = thread, media = thread.originalPost.attachments, message = null)
            }
            observeSelectedThread(thread.key)
        }

        fun preloadSelectedThread() {
            val thread = _uiState.value.selectedThread ?: return
            viewModelScope.launch {
                val total =
                    thread.originalPost.attachments.size
                        .coerceAtLeast(1)
                _uiState.update {
                    it.copy(
                        preloadingThread = true,
                        progressMessage = buildProgressMessage(1, total, "thread media"),
                        progressValue = 0.2f,
                        message = null,
                    )
                }
                when (val result = threadRepository.refreshThread(providerId, thread.key.board, thread.key.thread)) {
                    is OrbinResult.Success ->
                        _uiState.update {
                            it.copy(
                                media =
                                    result.data.allPosts
                                        .flatMap { post -> post.attachments }
                                        .toImmutableList(),
                                preloadingThread = false,
                                progressMessage = null,
                                progressValue = 1f,
                                message = "Thread preloaded",
                            )
                        }
                    is OrbinResult.Failure ->
                        _uiState.update {
                            it.copy(
                                preloadingThread = false,
                                progressMessage = null,
                                progressValue = 0f,
                                message =
                                    result.error.message ?: "Unable to preload thread",
                            )
                        }
                }
            }
        }

        private fun loadBoards() {
            viewModelScope.launch {
                runCatching { providerRegistry.require(providerId).getBoards() }
                    .onSuccess { boards ->
                        val selected = boards.firstOrNull()
                        _uiState.update {
                            it.copy(
                                boards = boards.toImmutableList(),
                                selectedBoard = selected,
                                loadingBoards = false,
                                loadingThreads = selected != null,
                                message = if (selected == null) "No boards available" else null,
                            )
                        }
                        if (selected != null) loadThreads(selected)
                    }.onFailure { error ->
                        _uiState.update {
                            it.copy(loadingBoards = false, message = error.message ?: "Unable to load boards")
                        }
                    }
            }
        }

        private fun loadThreads(board: Board) {
            viewModelScope.launch {
                runCatching { provider.getCatalog(CatalogRequest(providerId, board.id)) }
                    .onSuccess { threads ->
                        val selected =
                            threads.firstOrNull { it.originalPost.attachments.isNotEmpty() } ?: threads.firstOrNull()
                        _uiState.update {
                            it.copy(
                                threads = threads.toImmutableList(),
                                selectedThread = selected,
                                media = selected?.originalPost?.attachments ?: persistentListOf(),
                                loadingThreads = false,
                                message =
                                    if (selected ==
                                        null
                                    ) {
                                        "No threads available for /${board.id.value}/"
                                    } else {
                                        null
                                    },
                            )
                        }
                        if (selected != null) observeSelectedThread(selected.key)
                    }.onFailure { error ->
                        _uiState.update {
                            it.copy(loadingThreads = false, message = error.message ?: "Unable to load threads")
                        }
                    }
            }
        }

        private fun observeSelectedThread(key: ThreadKey) {
            threadJob?.cancel()
            threadJob =
                viewModelScope.launch {
                    threadRepository.observeThread(key).collectLatest { result ->
                        if (_uiState.value.selectedThread?.key != key) return@collectLatest
                        if (result is OrbinResult.Success) {
                            _uiState.update {
                                it.copy(
                                    media =
                                        result.data.allPosts
                                            .flatMap { post ->
                                                post.attachments
                                            }.toImmutableList(),
                                )
                            }
                        }
                    }
                }
        }
    }
