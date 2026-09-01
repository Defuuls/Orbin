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
import com.orbin.core.model.hiddenTagTokens
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.core.model.matchesFilterTokens
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.DownloadRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SavedThreadRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.usecase.ObserveThreadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
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

/** Observes a thread, records reading history, and owns reader actions/state. */
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
        private val savedThreadRepository: SavedThreadRepository,
    ) : ViewModel() {
        val title: String = savedStateHandle.get<String>("title").orEmpty()

        private val provider = ProviderId(savedStateHandle.get<String>("provider").orEmpty())
        private val board = BoardId(savedStateHandle.get<String>("board").orEmpty())
        private val threadId = ThreadId(savedStateHandle.get<Long>("thread") ?: 0L)
        private val threadKey = ThreadKey(provider, board, threadId)
        private val key = ThreadKey(provider, board, threadId)

        private var loadedThread: Thread? = null

        private val _initialScrollPosition = MutableStateFlow<ThreadScrollPosition?>(null)

        val initialScrollPosition: StateFlow<ThreadScrollPosition?> = _initialScrollPosition.asStateFlow()

        init {
            viewModelScope.launch {
                val existing = historyRepository.getEntry(key)
                _initialScrollPosition.value =
                    existing?.lastReadPostId?.let { ThreadScrollPosition(it, existing.lastReadOffsetPx) }
            }
        }

        private val _exportMessage = MutableStateFlow<String?>(null)

        /** Shared one-shot feedback channel for exports, saves and download queue actions. */
        val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

        private val reloads = MutableStateFlow(0)
        private val _isRefreshing = MutableStateFlow(false)

        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        private val mediaFilter: StateFlow<MediaFilter> =
            settingsRepository.settings
                .map { it.mediaFilter }
                .stateIn(viewModelScope, SharingStarted.Eagerly, MediaFilter.ALL)

        private val hiddenTokens: StateFlow<Set<String>> =
            settingsRepository.settings
                .map { it.hiddenTagTokens() }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

        private val includeHarsh: StateFlow<Boolean> =
            settingsRepository.settings
                .map { it.harshContentFilter }
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)

        val uiState: StateFlow<ThreadUiState> =
            combine(
                reloads
                    .flatMapLatest { attempt ->
                        observeThread(provider, board, threadId, forceRefresh = attempt > 0)
                    }.onEach { result ->
                        if (result is OrbinResult.Success) onThreadLoaded(result.data)
                        _isRefreshing.value = false
                    },
                mediaFilter,
                hiddenTokens,
                includeHarsh,
            ) { result, filter, hidden, harsh ->
                result.fold(
                    onSuccess = {
                        if (it.isPermanentlyFiltered(harsh)) {
                            ThreadUiState.Blocked
                        } else {
                            ThreadUiState.Success(it.filteredBy(filter).hidingMatches(hidden, harsh))
                        }
                    },
                    onFailure = { error ->
                        val saved = savedThreadRepository.load(threadKey)
                        when {
                            saved == null -> ThreadUiState.Error(error.message)
                            saved.isPermanentlyFiltered(harsh) -> ThreadUiState.Blocked
                            else ->
                                ThreadUiState.Success(
                                    thread = saved.filteredBy(filter).hidingMatches(hidden, harsh),
                                    fromSavedCopy = true,
                                )
                        }
                    },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ThreadUiState.Loading)

        /** The full bookmark supplies both watched state and the last seen reply count. */
        val bookmark: StateFlow<Bookmark?> =
            bookmarkRepository
                .observeBookmark(key)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        val isBookmarked: StateFlow<Boolean> =
            bookmark
                .map { it != null }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

        /**
         * First reply after the bookmark's last seen reply count. Null means there is no unread
         * reply (or the thread is not watched), so the UI does not offer a dead jump target.
         */
        val firstUnreadPostId: StateFlow<PostId?> =
            combine(bookmark, uiState) { savedBookmark, state ->
                val success = state as? ThreadUiState.Success
                if (savedBookmark == null || success == null) {
                    null
                } else {
                    success.thread.replies.getOrNull(savedBookmark.lastSeenReplyCount)?.id
                }
            }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun refresh() {
            _isRefreshing.value = true
            reloads.update { it + 1 }
        }

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
            val attachments =
                thread.allPosts
                    .flatMap { it.attachments }
                    .filteredBy(mediaFilter.value)
            viewModelScope.launch {
                attachments.forEach { attachment ->
                    downloadRepository.enqueue(
                        url = attachment.sourceUrl,
                        fileName = attachment.downloadFileName(),
                        boardId = board.value,
                        threadId = threadId.value,
                        threadTitle = threadTitle,
                    )
                }
                _exportMessage.value =
                    when (attachments.size) {
                        0 -> "No matching media to download"
                        1 -> "Queued 1 file for download"
                        else -> "Queued ${attachments.size} files for download"
                    }
            }
        }

        val isSaved: StateFlow<Boolean> =
            savedThreadRepository
                .isSaved(threadKey)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

        fun saveThread() {
            val thread = loadedThread ?: return
            viewModelScope.launch {
                savedThreadRepository.save(thread)
                _exportMessage.value = "Saved this thread's text (${thread.allPosts.size} posts)"
            }
        }

        fun forgetSavedThread() {
            viewModelScope.launch {
                savedThreadRepository.forget(threadKey)
                _exportMessage.value = "Deleted the saved copy"
            }
        }

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

        fun saveScrollPosition(
            postId: PostId,
            offsetPx: Int,
        ) {
            _initialScrollPosition.value = ThreadScrollPosition(postId, offsetPx)
            viewModelScope.launch {
                historyRepository.updateScrollPosition(key, postId, offsetPx)
            }
        }

        private fun onThreadLoaded(thread: Thread) {
            if (thread.isPermanentlyFiltered()) return
            loadedThread = thread
            viewModelScope.launch {
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
                    .replace(UNSAFE_FILENAME_CHARS, "_")
            return "${board.value}_${threadId.value}_${id}_$cleanName"
        }

        private companion object {
            val UNSAFE_FILENAME_CHARS = Regex("""[\\/:*?"<>|]""")
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

data class ThreadScrollPosition(
    val postId: PostId,
    val offsetPx: Int,
)

internal fun Thread.hidingMatches(
    tokens: Set<String>,
    includeHarsh: Boolean = false,
): Thread =
    copy(
        replies =
            replies
                .filterNot { reply -> reply.matchesFilterTokens(tokens, includeHarsh) }
                .toImmutableList(),
    )
