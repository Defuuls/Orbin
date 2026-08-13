package com.orbin.feature.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.common.result.fold
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.MediaFilter
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThumbnailSize
import com.orbin.core.model.filteredBy
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.DownloadRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.usecase.ObserveThreadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Observes a thread, records reading history, and toggles bookmarking.
 *
 * [flatMapLatest], which restarts the load on refresh, is still experimental. The opt-in sits at
 * class level to match `SubscribedFeedViewModel` and the other ViewModels that switch streams the
 * same way.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ThreadViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        observeThread: ObserveThreadUseCase,
        private val bookmarkRepository: BookmarkRepository,
        private val downloadRepository: DownloadRepository,
        private val historyRepository: HistoryRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val title: String = savedStateHandle.get<String>("title").orEmpty()

        private val provider = ProviderId(savedStateHandle.get<String>("provider").orEmpty())
        private val board = BoardId(savedStateHandle.get<String>("board").orEmpty())
        private val threadId = ThreadId(savedStateHandle.get<Long>("thread") ?: 0L)
        private val key = ThreadKey(provider, board, threadId)

        private var loadedThread: Thread? = null

        private val _initialScrollPosition = MutableStateFlow<ThreadScrollPosition?>(null)

        /** Where the reader left off last time, loaded once when the thread is opened. */
        val initialScrollPosition: StateFlow<ThreadScrollPosition?> = _initialScrollPosition.asStateFlow()

        init {
            viewModelScope.launch {
                val existing = historyRepository.getEntry(key)
                _initialScrollPosition.value =
                    existing?.lastReadPostId?.let { ThreadScrollPosition(it, existing.lastReadOffsetPx) }
            }
        }

        private val _exportMessage = MutableStateFlow<String?>(null)

        /** One-shot status message for the last [exportLinks] call; cleared via [consumeExportMessage]. */
        val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

        /** Incremented by [refresh]; every value past the first is a user-initiated reload. */
        private val reloads = MutableStateFlow(0)

        private val _isRefreshing = MutableStateFlow(false)

        /** Drives the pull-to-refresh indicator; false again once the reload settles. */
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        /**
         * The reader's media filter. Eager so [downloadAllMedia] can read it synchronously and
         * download exactly what the thread is showing.
         */
        private val mediaFilter: StateFlow<MediaFilter> =
            settingsRepository.settings
                .map { it.mediaFilter }
                .stateIn(viewModelScope, SharingStarted.Eagerly, MediaFilter.ALL)

        val uiState: StateFlow<ThreadUiState> =
            combine(
                reloads
                    .flatMapLatest { attempt ->
                        // The initial load may serve the cache for instant display. A reload must
                        // not: the reader is asking whether there are new replies, and the answer
                        // cannot be the snapshot they are already looking at.
                        observeThread(provider, board, threadId, forceRefresh = attempt > 0)
                    }.onEach { result ->
                        // Deliberately upstream of the filter, so history and the loaded snapshot
                        // are recorded once per load rather than again on every settings change.
                        if (result is OrbinResult.Success) onThreadLoaded(result.data)
                        _isRefreshing.value = false
                    },
                mediaFilter,
            ) { result, filter ->
                result.fold(
                    onSuccess = { ThreadUiState.Success(it.filteredBy(filter)) },
                    onFailure = { ThreadUiState.Error(it.message) },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ThreadUiState.Loading)

        /** Reloads the thread from the network, bypassing the cache. */
        fun refresh() {
            _isRefreshing.value = true
            reloads.update { it + 1 }
        }

        val isBookmarked: StateFlow<Boolean> =
            bookmarkRepository
                .observeBookmark(key)
                .map { it != null }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

        // The default for this session, from Settings. The grid's size toggle can temporarily
        // override it without changing the persisted preference.
        val thumbnailSize: StateFlow<ThumbnailSize> =
            settingsRepository.settings
                .map { it.thumbnailSize }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ThumbnailSize.MEDIUM)

        val mediaScrollEnabled: StateFlow<Boolean> =
            settingsRepository.settings
                .map { it.mediaScrollThreadView }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

        fun toggleBookmark() {
            viewModelScope.launch {
                if (isBookmarked.value) {
                    bookmarkRepository.removeBookmark(key)
                } else {
                    bookmarkRepository.addBookmark(currentBookmark())
                }
            }
        }

        fun downloadAllMedia() {
            val thread = loadedThread ?: return
            val threadTitle = title.ifBlank { thread.subject }
            viewModelScope.launch {
                thread.allPosts
                    .flatMap { it.attachments }
                    // Downloads what the thread is showing: a reader browsing videos only does not
                    // expect "download all media" to pull in every image as well.
                    .filteredBy(mediaFilter.value)
                    .forEach { attachment ->
                        downloadRepository.enqueue(
                            url = attachment.sourceUrl,
                            fileName = attachment.downloadFileName(),
                            boardId = board.value,
                            threadId = threadId.value,
                            threadTitle = threadTitle,
                        )
                    }
            }
        }

        /** Exports every external link found in the thread's posts as a `.txt` file, one per line. */
        fun exportLinks() {
            val thread = loadedThread ?: return
            viewModelScope.launch {
                val links = thread.allPosts.flatMap { it.comment.externalLinks }.distinct()
                if (links.isEmpty()) {
                    _exportMessage.value = "No links found in this thread"
                    return@launch
                }
                val fileName = "orbin_links_${board.value}_${threadId.value}.txt"
                val saved = downloadRepository.writeTextFile(fileName, links.joinToString("\n"))
                _exportMessage.value =
                    if (saved) {
                        "Saved ${links.size} link${if (links.size == 1) "" else "s"} to $fileName"
                    } else {
                        "Couldn't save links to the saved media folder"
                    }
            }
        }

        fun consumeExportMessage() {
            _exportMessage.value = null
        }

        /** Persists where the reader has scrolled to, so reopening this thread resumes there. */
        fun saveScrollPosition(
            postId: PostId,
            offsetPx: Int,
        ) {
            viewModelScope.launch {
                historyRepository.updateScrollPosition(key, postId, offsetPx)
            }
        }

        private fun onThreadLoaded(thread: Thread) {
            loadedThread = thread
            viewModelScope.launch {
                // record() preserves any existing scroll anchor; only metadata (title, thumbnail,
                // last-visited time) is refreshed here.
                historyRepository.record(
                    HistoryEntry(
                        key = key,
                        title = title.ifBlank { thread.subject ?: "/${board.value}/" },
                        thumbnailUrl =
                            thread.originalPost.attachments
                                .firstOrNull()
                                ?.thumbnailUrl,
                        lastVisitedMillis = System.currentTimeMillis(),
                        lastReadPostId = thread.originalPost.id,
                    ),
                )
            }
        }

        private fun currentBookmark(): Bookmark {
            val thread = loadedThread
            return Bookmark(
                key = key,
                title = title.ifBlank { thread?.subject ?: "/${board.value}/" },
                thumbnailUrl =
                    thread
                        ?.originalPost
                        ?.attachments
                        ?.firstOrNull()
                        ?.thumbnailUrl,
                createdAtMillis = System.currentTimeMillis(),
                lastSeenReplyCount = thread?.stats?.replyCount ?: 0,
                latestReplyCount = thread?.stats?.replyCount ?: 0,
            )
        }

        private fun com.orbin.core.model.MediaAttachment.downloadFileName(): String {
            val cleanName =
                originalFileName
                    .ifBlank { id }
                    .replace(Regex("""[\\/:*?"<>|]"""), "_")
            return "${board.value}_${threadId.value}_${id}_$cleanName"
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

/** A saved reading position: the last post the reader had scrolled to, and its pixel offset. */
data class ThreadScrollPosition(
    val postId: PostId,
    val offsetPx: Int,
)
