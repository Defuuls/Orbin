package com.orbin.feature.gallery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.PlaybackPreferences
import com.orbin.core.model.PlaybackSettings
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.domain.repository.DownloadRepository
import com.orbin.domain.repository.SavedThreadRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.usecase.ObserveThreadUseCase
import com.orbin.media.ImagePreloader
import com.orbin.media.image.ImageClipboard
import com.orbin.media.image.ImageCopyResult
import com.orbin.media.video.VideoPreloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class GalleryDownloadUiState(
    val isBusy: Boolean = false,
    val label: String? = null,
    val progressValue: Float = 0f,
)

/** Loading and unavailable threads must not look like successfully loaded, empty media lists. */
sealed interface GalleryContentState {
    data object Loading : GalleryContentState

    data object SelectedUnavailable : GalleryContentState

    data class Ready(
        val media: ImmutableList<MediaAttachment>,
        val threadMedia: ImmutableList<MediaAttachment>,
        val threadMediaIndices: ImmutableList<Int>,
    ) : GalleryContentState {
        /** Engines can reuse an attachment id/URL on different posts in the same thread. */
        fun pageKey(page: Int): String = "${threadMediaIndices[page]}:${media[page].id}"
    }

    data class Error(
        val message: String,
    ) : GalleryContentState
}

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
        private val observeThread: ObserveThreadUseCase,
        private val downloadRepository: DownloadRepository,
        settingsRepository: SettingsRepository,
        private val savedThreadRepository: SavedThreadRepository,
        private val imageClipboard: ImageClipboard,
        private val imagePreloader: ImagePreloader,
        private val videoPreloader: VideoPreloader,
    ) : ViewModel() {
        /**
         * Copies the image on screen. Routed through the ViewModel so the fetch uses the app's
         * injected HTTP client — DNS-over-HTTPS, configured user-agent and all — rather than a
         * connection the UI opens for itself.
         */
        suspend fun copyImage(imageUrl: String): ImageCopyResult = imageClipboard.copy(imageUrl)

        private val startIndex: Int = savedStateHandle.get<Int>("startIndex") ?: 0
        val videosOnly: Boolean = savedStateHandle.get<Boolean>("videosOnly") ?: false

        /**
         * The file to open at, when the caller knew which attachment it wanted but not where that
         * attachment falls in the thread. Resolved against the loaded media in [initialPageIn].
         */
        private val attachmentId: String? = savedStateHandle.get<String>("attachmentId")
        private val thumbnailUrl: String? = savedStateHandle.get<String>("thumbnailUrl")

        /** Exact attachment identity wins; Lynx catalog IDs may differ, so a unique preview is next. */
        fun initialPageIn(media: List<MediaAttachment>): Int =
            galleryInitialPage(media, attachmentId, thumbnailUrl, startIndex)

        /** Drives video autoplay / mute in the gallery from the user's media settings. */
        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateIn(
                    viewModelScope,
                    SharingStarted.Eagerly,
                    // Wait for persisted preferences before starting a clip or fetching its neighbours.
                    AppSettings.Default.copy(
                        playbackPreferences =
                            PlaybackPreferences(
                                defaults = PlaybackSettings(autoplay = false, preloadNextClip = false),
                            ),
                    ),
                )

        private val provider = ProviderId(savedStateHandle.get<String>("provider").orEmpty())
        private val board = BoardId(savedStateHandle.get<String>("board").orEmpty())
        private val threadId = ThreadId(savedStateHandle.get<Long>("thread") ?: 0L)
        private val threadKey = ThreadKey(provider, board, threadId)

        fun playbackSettings() = settings.value.playbackPreferences.resolve(provider, board)

        private val _downloadState = MutableStateFlow(GalleryDownloadUiState())
        val downloadState: StateFlow<GalleryDownloadUiState> = _downloadState.asStateFlow()
        private val downloadMutex = Mutex()

        /**
         * The thread's media, in reading order. Filtered exactly as the thread view filters it, so
         * the page a tapped thumbnail opens at is the one the reader tapped — which is also why
         * the permanent filter is applied here in the same two ways the thread view applies it:
         * whole posts it catches, then individual files it catches by name.
         */
        private val reload = MutableStateFlow(0)

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val content: StateFlow<GalleryContentState> =
            reload
                .flatMapLatest { attempt ->
                    observeThread(provider, board, threadId, forceRefresh = attempt > 0)
                        .map(::contentFor)
                        .onStart { emit(GalleryContentState.Loading) }
                        .catch { emit(GalleryContentState.Error("Couldn't load the thread. Try again.")) }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GalleryContentState.Loading)

        val media: StateFlow<ImmutableList<MediaAttachment>> =
            content
                .map { (it as? GalleryContentState.Ready)?.media ?: persistentListOf() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), persistentListOf())

        private suspend fun contentFor(result: OrbinResult<Thread>): GalleryContentState {
            val thread =
                when (result) {
                    is OrbinResult.Success -> result.data
                    is OrbinResult.Failure -> savedThreadRepository.load(threadKey)
                }
            if (thread == null) {
                return GalleryContentState.Error(
                    (result as? OrbinResult.Failure)?.error?.message ?: "Couldn't load the thread. Try again.",
                )
            }
            val allMedia =
                if (thread.isPermanentlyFiltered()) {
                    persistentListOf()
                } else {
                    thread.allPosts
                        .filterNot { it.isPermanentlyFiltered() }
                        .flatMap { it.attachments }
                        .filterNot { it.isPermanentlyFiltered() }
                        .toImmutableList()
                }
            val visibleIndices =
                allMedia.indices
                    .filter { index -> !videosOnly || allMedia[index].type == MediaType.VIDEO }
                    .toImmutableList()
            val visibleMedia = if (videosOnly) visibleIndices.map(allMedia::get).toImmutableList() else allMedia
            if ((attachmentId != null || thumbnailUrl != null) && initialPageIn(visibleMedia) < 0) {
                return GalleryContentState.SelectedUnavailable
            }
            return GalleryContentState.Ready(visibleMedia, allMedia, visibleIndices)
        }

        fun retry() {
            reload.value++
        }

        /** A filtered video page still points back to the correct file in its original thread. */
        fun threadMediaIndexAt(page: Int): Int? {
            val loaded = content.value as? GalleryContentState.Ready ?: return null
            return loaded.threadMediaIndices.getOrNull(page)
        }

        private var prefetchJob: Job? = null
        private var videoPrefetchJob: Job? = null
        private val videoPrefetchOwner = Any()

        /**
         * Prefetch the settled page and its immediate neighbours so swipes feel instant without
         * keeping an unstructured coroutine scope alive after the gallery closes.
         */
        fun prefetchAround(
            settledPage: Int,
            items: List<MediaAttachment>,
        ) {
            prefetchJob?.cancel()
            videoPrefetchJob?.cancel()
            if (items.isEmpty()) return
            val neighbours = if (playbackSettings().preloadNextClip) -1..1 else 0..0
            val urls =
                neighbours.mapNotNull { delta ->
                    val item = items.getOrNull(settledPage + delta) ?: return@mapNotNull null
                    if (item.isSpoiler) return@mapNotNull null
                    // Coil cannot decode video/audio. Fetching their full source here duplicates
                    // Media3's stream and can make the active player wait for a speculative request.
                    val image = item.type == MediaType.IMAGE || item.type == MediaType.ANIMATED_IMAGE
                    val url = if (delta == 0 && image) item.sourceUrl else item.thumbnailUrl
                    url.takeIf { it.isNotBlank() }
                }
            prefetchJob = imagePreloader.prefetchBatch(urls, viewModelScope)
            if (playbackSettings().preloadNextClip) {
                items.getOrNull(settledPage + 1)?.takeIf { it.isPlayable && !it.isSpoiler }?.let { next ->
                    videoPrefetchJob = videoPreloader.prefetch(next.sourceUrl, viewModelScope, videoPrefetchOwner)
                }
            } else {
                videoPreloader.clear(videoPrefetchOwner)
            }
        }

        override fun onCleared() {
            videoPrefetchJob?.cancel()
            videoPreloader.clear(videoPrefetchOwner)
            super.onCleared()
        }

        /** Queues all other thread media, including files before the visible page. */
        fun downloadRestOfThread(visiblePage: Int) {
            val ready = content.value as? GalleryContentState.Ready ?: return
            enqueueDownloads(galleryRemainingMedia(ready, visiblePage))
        }

        fun download(attachment: MediaAttachment) = enqueueDownloads(listOf(attachment))

        private fun enqueueDownloads(attachments: List<MediaAttachment>) {
            if (attachments.isEmpty()) return
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                // Persist the confirmed queue even if the viewer closes. Actual transfers remain
                // owned by the repository/WorkManager, never by this screen or its player.
                withContext(NonCancellable) {
                    downloadMutex.withLock {
                        try {
                            attachments.forEachIndexed { index, attachment ->
                                _downloadState.value =
                                    GalleryDownloadUiState(
                                        isBusy = true,
                                        label =
                                            buildProgressMessage(
                                                index + 1,
                                                attachments.size,
                                                attachment.originalFileName,
                                            ),
                                        progressValue = (index + 1f) / attachments.size,
                                    )
                                runCatching {
                                    downloadRepository.enqueue(
                                        url = attachment.sourceUrl,
                                        fileName = attachment.originalFileName,
                                        boardId = board.value,
                                        threadId = threadId.value,
                                    )
                                }
                            }
                        } finally {
                            _downloadState.value = GalleryDownloadUiState()
                        }
                    }
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

/** The viewer may show videos only; its index must first map back to the complete thread. */
internal fun galleryRemainingMedia(
    ready: GalleryContentState.Ready,
    visiblePage: Int,
): List<MediaAttachment> {
    val current = ready.threadMediaIndices.getOrNull(visiblePage) ?: return emptyList()
    if (current !in ready.threadMedia.indices) return emptyList()
    return ready.threadMedia.filterIndexed { index, _ -> index != current }
}

/** Explicit anchors never fall through to an unrelated first file; index-only thread entries retain their index. */
internal fun galleryInitialPage(
    media: List<MediaAttachment>,
    attachmentId: String?,
    thumbnailUrl: String?,
    startIndex: Int,
): Int {
    val byId = attachmentId?.takeIf(String::isNotBlank)?.let { id -> media.indexOfFirst { it.id == id } } ?: -1
    if (byId >= 0) return byId
    val byPreview =
        thumbnailUrl?.takeIf(String::isNotBlank)?.let { preview ->
            media.indices
                .filter {
                    media[it].thumbnailUrl ==
                        preview
                }.singleOrNull()
        }
    return byPreview ?: if (attachmentId == null && thumbnailUrl == null) startIndex else -1
}
