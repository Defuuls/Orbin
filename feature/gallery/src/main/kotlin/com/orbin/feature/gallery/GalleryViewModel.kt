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
import com.orbin.domain.repository.DownloadRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.usecase.ObserveThreadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GalleryDownloadUiState(
    val isBusy: Boolean = false,
    val label: String? = null,
    val progressValue: Float = 0f,
)

fun buildProgressMessage(current: Int, total: Int, label: String): String {
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
    ) : ViewModel() {
        val startIndex: Int = savedStateHandle.get<Int>("startIndex") ?: 0

        /** Drives video autoplay / mute in the gallery from the user's media settings. */
        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings.Default)

        private val provider = ProviderId(savedStateHandle.get<String>("provider").orEmpty())
        private val board = BoardId(savedStateHandle.get<String>("board").orEmpty())
        private val threadId = ThreadId(savedStateHandle.get<Long>("thread") ?: 0L)

        private val _downloadState = MutableStateFlow(GalleryDownloadUiState())
        val downloadState: StateFlow<GalleryDownloadUiState> = _downloadState.asStateFlow()

        val media: StateFlow<ImmutableList<MediaAttachment>> =
            observeThread(provider, board, threadId)
                .map { result ->
                    when (result) {
                        is OrbinResult.Success ->
                            result.data.allPosts
                                .flatMap { it.attachments }
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
                runCatching { downloadRepository.enqueue(attachment.sourceUrl, attachment.originalFileName) }
                _downloadState.value = GalleryDownloadUiState()
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
