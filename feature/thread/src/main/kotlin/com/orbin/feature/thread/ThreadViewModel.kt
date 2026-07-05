package com.orbin.feature.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.common.result.fold
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThumbnailSize
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.DownloadRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.usecase.ObserveThreadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Observes a thread, records reading history, and toggles bookmarking. */
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

        private val _exportMessage = MutableStateFlow<String?>(null)

        /** One-shot status message for the last [exportLinks] call; cleared via [consumeExportMessage]. */
        val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

        val uiState: StateFlow<ThreadUiState> =
            observeThread(provider, board, threadId)
                .onEach { result -> if (result is OrbinResult.Success) onThreadLoaded(result.data) }
                .map { result ->
                    result.fold(
                        onSuccess = { ThreadUiState.Success(it) },
                        onFailure = { ThreadUiState.Error(it.message) },
                    )
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ThreadUiState.Loading)

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
            viewModelScope.launch {
                thread.allPosts
                    .flatMap { it.attachments }
                    .forEach { attachment ->
                        downloadRepository.enqueue(
                            url = attachment.sourceUrl,
                            fileName = attachment.downloadFileName(),
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

        private fun onThreadLoaded(thread: Thread) {
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
                    .replace(Regex("""[\\/:*?"<>|]"""), "_")
            return "${board.value}_${threadId.value}_${id}_$cleanName"
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
