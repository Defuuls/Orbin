package com.orbin.feature.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaFilter
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.filteredBy
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.repository.ThreadRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.media.preload.MediaPreloader
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GalleryBrowserViewModel
    @Inject
    constructor(
        providerRegistry: ProviderRegistry,
        observeActiveProvider: ObserveActiveProviderUseCase,
        private val threadRepository: ThreadRepository,
        private val mediaPreloader: MediaPreloader,
        private val settingsRepository: SettingsRepository,
        private val boardPreferencesRepository: BoardPreferencesRepository,
    ) : ViewModel() {
        private val activeProvider: StateFlow<ImageBoardProvider> =
            observeActiveProvider()
                .stateIn(viewModelScope, SharingStarted.Eagerly, providerRegistry.default())
        private var threadJob: Job? = null

        private val _uiState =
            MutableStateFlow(GalleryBrowserUiState(provider = activeProvider.value.metadata.id))
        val uiState: StateFlow<GalleryBrowserUiState> = _uiState.asStateFlow()

        private val mediaFilter: StateFlow<MediaFilter> =
            settingsRepository.settings
                .map { it.mediaFilter }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.Eagerly, MediaFilter.ALL)

        /**
         * Everything the selected thread has, before [mediaFilter] is applied. Kept so changing the
         * filter re-filters the thread already on screen instead of needing it reselected.
         */
        private var unfilteredMedia: List<MediaAttachment> = emptyList()

        init {
            mediaFilter
                .onEach { filter ->
                    _uiState.update { it.copy(media = unfilteredMedia.visibleUnder(filter)) }
                }.launchIn(viewModelScope)

            activeProvider
                .flatMapLatest { provider ->
                    boardPreferencesRepository
                        .observeSubscribedBoards(provider.metadata.id)
                        .distinctUntilChanged()
                        .map { subscribedIds -> provider to subscribedIds }
                }.onEach { (provider, subscribedIds) ->
                    threadJob?.cancel()
                    unfilteredMedia = emptyList()
                    _uiState.value = GalleryBrowserUiState(provider = provider.metadata.id)
                    loadBoards(provider, subscribedIds)
                }.launchIn(viewModelScope)
        }

        fun selectBoard(board: Board) {
            if (_uiState.value.selectedBoard?.id == board.id) return
            unfilteredMedia = emptyList()
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
            val media = rememberMedia(thread.originalPost.attachments)
            _uiState.update {
                it.copy(selectedThread = thread, media = media, message = null)
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
                        // Only the media on screen is warmed: preloading what the filter hides
                        // would spend the user's data on files they have chosen not to see.
                        val media =
                            rememberMedia(result.data.allPosts.flatMap { post -> post.attachments })
                        _uiState.update {
                            it.copy(
                                media = media,
                                progressMessage = buildProgressMessage(1, media.size.coerceAtLeast(1), "media"),
                                progressValue = PRELOAD_START_PROGRESS,
                            )
                        }
                        val settings = settingsRepository.settings.first()
                        val warmed =
                            mediaPreloader.preload(
                                media,
                                option = settings.preloadOption,
                                throttleMode = settings.preloadThrottleMode,
                            ) { current, total, label ->
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
                                message = result.error.message,
                            )
                        }
                }
            }
        }

        private fun loadBoards(
            provider: ImageBoardProvider,
            subscribedIds: Set<BoardId>,
        ) {
            viewModelScope.launch {
                runCatching { provider.getBoards() }
                    .onSuccess { boards ->
                        // The gallery browses subscribed boards only, mirroring the feed's rules.
                        val hideNsfw = settingsRepository.settings.first().hideNsfwBoards
                        val subscribed =
                            boards
                                .filter { it.id in subscribedIds }
                                .filterNot { board -> hideNsfw && board.isNsfw }
                                .sortedBy { it.id.value }
                        val selected = subscribed.firstOrNull()
                        _uiState.update {
                            it.copy(
                                boards = subscribed.toImmutableList(),
                                selectedBoard = selected,
                                loadingBoards = false,
                                loadingThreads = selected != null,
                                message =
                                    if (selected == null) {
                                        "No subscribed boards — subscribe to boards to browse their media"
                                    } else {
                                        null
                                    },
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
                        // Opens on a thread that has something to show under the current filter.
                        val filter = mediaFilter.value
                        val selected =
                            threads.firstOrNull {
                                it.originalPost.attachments
                                    .filteredBy(filter)
                                    .isNotEmpty()
                            }
                                ?: threads.firstOrNull { it.originalPost.attachments.isNotEmpty() }
                                ?: threads.firstOrNull()
                        val media = rememberMedia(selected?.originalPost?.attachments ?: persistentListOf())
                        _uiState.update {
                            it.copy(
                                threads = threads.toImmutableList(),
                                selectedThread = selected,
                                media = media,
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
                            val media = rememberMedia(result.data.allPosts.flatMap { post -> post.attachments })
                            _uiState.update { it.copy(media = media) }
                        }
                    }
                }
        }

        /** Records [media] as the selected thread's full set and returns what the filter shows. */
        private fun rememberMedia(media: List<MediaAttachment>): ImmutableList<MediaAttachment> {
            unfilteredMedia = media
            return media.visibleUnder(mediaFilter.value)
        }

        private fun List<MediaAttachment>.visibleUnder(filter: MediaFilter): ImmutableList<MediaAttachment> =
            filteredBy(filter).toImmutableList()

        private companion object {
            const val PRELOAD_START_PROGRESS = 0.1f
        }
    }
