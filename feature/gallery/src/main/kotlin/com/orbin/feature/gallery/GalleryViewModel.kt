package com.orbin.feature.gallery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.filteredBy
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.domain.repository.DownloadRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.usecase.ObserveThreadUseCase
import com.orbin.media.image.ImageClipboard
import com.orbin.media.image.ImageCopyResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GalleryDownloadUiState(
    val isBusy: Boolean = false,
    val label: String? = null,
    val progressValue: Float = 0f,
)

fun buildProgressMessage(
    current: Int,
    total: Int,
    label: String,
): String {
    val cleanedLabel = label.trim().ifBlank { "media" }
    return "$current/$total · $cleanedLabel"
}

/** Collects all media in a thread for the swipeable gallery, starting at [startIndex]. */
@HiltViewModel
class GalleryViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        observeThread: ObserveThreadUseCase,
        private val downloadRepository: DownloadRepository,
        settingsRepository: SettingsRepository,
        private val imageClipboard: ImageClipboard,
    ) : ViewModel() {
        /**
         * Copies the image on screen. Routed through the ViewModel so the fetch uses the app's
         * injected HTTP client — DNS-over-HTTPS, configured user-agent and all — rather than a
         * connection the UI opens for itself.
         */
        suspend fun copyImage(imageUrl: String): ImageCopyResult = imageClipboard.copy(imageUrl)

        private val startIndex: Int = savedStateHandle.get<Int>("startIndex") ?: 0

        /**
         * The file to open at, when the caller knew which attachment it wanted but not where that
         * attachment falls in the thread. Resolved against the loaded media in [initialPageIn].
         */
        private val attachmentId: String? = savedStateHandle.get<String>("attachmentId")

        /**
         * Which page the pager should open on, given the thread's [media].
         *
         * An index alone is only meaningful to a caller that built the same list this screen shows
         * — the thread view and the gallery browser do, the all-media wall does not: it holds a
         * catalog's files, which is a different list from the thread's. So an attachment id, when
         * one was passed, wins over the index, and falls back to it if the file is no longer in the
         * thread (deleted, or filtered out of this list).
         */
        fun initialPageIn(media: List<MediaAttachment>): Int {
            val byId = attachmentId?.let { id -> media.indexOfFirst { it.id == id } } ?: -1
            return if (byId >= 0) byId else startIndex
        }

        /** Drives video autoplay / mute in the gallery from the user's media settings. */
        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings.Default)

        private val provider = ProviderId(savedStateHandle.get<String>("provider").orEmpty())
        private val board = BoardId(savedStateHandle.get<String>("board").orEmpty())
        private val threadId = ThreadId(savedStateHandle.get<Long>("thread") ?: 0L)

        private val _downloadState = MutableStateFlow(GalleryDownloadUiState())
        val downloadState: StateFlow<GalleryDownloadUiState> = _downloadState.asStateFlow()

        /**
         * The thread's media, in reading order. Filtered exactly as the thread view filters it, so
         * the page a tapped thumbnail opens at is the one the reader tapped — which is also why
         * the permanent filter is applied here in the same two ways the thread view applies it:
         * whole posts it catches, then individual files it catches by name.
         */
        val media: StateFlow<ImmutableList<MediaAttachment>> =
            combine(
                observeThread(provider, board, threadId),
                settings.map { it.mediaFilter }.distinctUntilChanged(),
            ) { result, filter ->
                when (result) {
                    is OrbinResult.Success ->
                        result.data.allPosts
                            .filterNot { it.isPermanentlyFiltered() }
                            .flatMap { it.attachments }
                            .filterNot { it.isPermanentlyFiltered() }
                            .filteredBy(filter)
                            .toImmutableList()
                    is OrbinResult.Failure -> persistentListOf()
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), persistentListOf())

        fun download(attachment: MediaAttachment) {
            viewModelScope.launch {
                _downloadState.value =
                    GalleryDownloadUiState(
                        isBusy = true,
                        label = buildProgressMessage(1, 1, attachment.originalFileName),
                        progressValue = 0.2f,
                    )
                runCatching {
                    downloadRepository.enqueue(
                        url = attachment.sourceUrl,
                        fileName = attachment.originalFileName,
                        boardId = board.value,
                        threadId = threadId.value,
                    )
                }
                _downloadState.value = GalleryDownloadUiState()
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
