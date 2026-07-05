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
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.media.preload.MediaPreloader
import com.orbin.provider.api.ImageBoardProvider
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
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
        providerRegistry: ProviderRegistry,
        observeActiveProvider: ObserveActiveProviderUseCase,
        private val threadRepository: ThreadRepository,
        private val mediaPreloader: MediaPreloader,
    ) : ViewModel() {
        private val activeProvider: StateFlow<ImageBoardProvider> =
            observeActiveProvider()
                .stateIn(viewModelScope, SharingStarted.Eagerly, providerRegistry.default())
        private var threadJob: Job? = null

        private val _uiState =
            MutableStateFlow(GalleryBrowserUiState(provider = activeProvider.value.metadata.id))
        val uiState: StateFlow<GalleryBrowserUiState> = _uiState.asStateFlow()

        init {
            activeProvider
                .onEach { provider ->
                    threadJob?.cancel()
                    _uiState.value = GalleryBrowserUiState(provider = provider.metadata.id)
                    loadBoards(provider)
                }.launchIn(viewModelScope)
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
            loadThreads(activeProvider.value, board)
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
                _uiState.update {
                    it.copy(
                        preloadingThread = true,
                        progressMessage = buildProgressMessage(1, 1, "refreshing thread"),
                        progressValue = PRELOAD_START_PROGRESS,
                        message = null,
                    )
                }
                val providerId = activeProvider.value.metadata.id
                when (val result = threadRepository.refreshThread(providerId, thread.key.board, thread.key.thread)) {
                    is OrbinResult.Success -> {
                        val media =
                            result.data.allPosts
                                .flatMap { post -> post.attachments }
                        _uiState.update {
                            it.copy(
                                media = media.toImmutableList(),
                                progressMessage = buildProgressMessage(1, media.size.coerceAtLeast(1), "media"),
                                progressValue = PRELOAD_START_PROGRESS,
                            )
                        }
                        val warmed =
                            mediaPreloader.preload(media) { current, total, label ->
                                _uiState.update {
                                    it.copy(
                                        progressMessage = buildProgressMessage(current, total, label),
                                        progressValue = current.toFloat() / total.toFloat(),
                                    )
                                }
                            }
                        _uiState.update {
                            it.copy(
                                preloadingThread = false,
                                progressMessage = null,
                                progressValue = 1f,
                                message =
                                    if (warmed > 0) {
                                        "Thread media preloaded"
                                    } else {
                                        "Thread refreshed"
                                    },
                            )
                        }
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

        private fun loadBoards(provider: ImageBoardProvider) {
            viewModelScope.launch {
                runCatching { provider.getBoards() }
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
                        if (selected != null) loadThreads(provider, selected)
                    }.onFailure { error ->
                        _uiState.update {
                            it.copy(loadingBoards = false, message = error.message ?: "Unable to load boards")
                        }
                    }
            }
        }

        private fun loadThreads(
            provider: ImageBoardProvider,
            board: Board,
        ) {
            viewModelScope.launch {
                runCatching { provider.getCatalog(CatalogRequest(provider.metadata.id, board.id)) }
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

        private companion object {
            const val PRELOAD_START_PROGRESS = 0.1f
        }
    }
