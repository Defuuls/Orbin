package com.orbin.feature.gallery

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.media.image.MediaThumbnail
import com.orbin.media.image.ZoomableImage
import com.orbin.media.video.SessionAudio
import com.orbin.media.video.VideoPlayer
import com.orbin.uinext.NextEmpty
import com.orbin.uinext.NextError
import com.orbin.uinext.NextLinearProgress
import com.orbin.uinext.NextLoading
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ReaderMediaAction
import com.orbin.uinext.ReaderMediaClose
import com.orbin.uinext.ReaderMediaTitle
import com.orbin.uinext.ReaderMediaToolbar
import com.orbin.uinext.next
import com.orbin.uinext.nextElevatedSurface
import com.orbin.uinext.readerMediaSwipeToClose
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderSize
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Full-screen, vertically swipeable media gallery for a thread. Images support pinch-zoom; videos
 * play with Media3. Close, Copy and Download sit in the shared, flat media toolbar; the download
 * action is provided by the host and wired to the download manager.
 */
@Composable
fun GalleryScreen(
    onClose: () -> Unit,
    onMediaPageChanged: (Int) -> Unit = {},
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val loaded = content as? GalleryContentState.Ready
    val media = loaded?.media.orEmpty()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playback = remember(settings) { viewModel.playbackSettings() }
    var playbackSpeed by rememberSaveable { mutableFloatStateOf(1f) }
    var viewerMuted by remember(playback.startMuted) {
        mutableStateOf(playback.startMuted && SessionAudio.muted)
    }
    val density = LocalDensity.current
    var toolbarHeight by remember { mutableStateOf(ReaderSize.appBar) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaPageChanged by rememberUpdatedState(onMediaPageChanged)
    LaunchedEffect(downloadState.errorMessage) {
        downloadState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeDownloadError()
        }
    }

    NextTheme(darkTheme = true) {
        if (media.isEmpty()) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                when (val state = content) {
                    GalleryContentState.Loading -> NextLoading()
                    GalleryContentState.SelectedUnavailable ->
                        NextError(
                            stringResource(R.string.gallery_selected_media_unavailable),
                            onRetry = viewModel::retry,
                        )
                    is GalleryContentState.Error -> NextError(state.message, onRetry = viewModel::retry)
                    is GalleryContentState.Ready ->
                        NextEmpty(
                            stringResource(
                                if (viewModel.videosOnly) R.string.gallery_no_thread_videos else R.string.gallery_empty,
                            ),
                            Modifier.fillMaxSize(),
                        )
                }
                ReaderMediaToolbar(Modifier.align(Alignment.TopCenter)) {
                    ReaderMediaClose(stringResource(R.string.gallery_close), onClose)
                    ReaderMediaTitle(stringResource(R.string.next_media_title), "", Modifier.weight(1f))
                }
            }
            return@NextTheme
        }

        val ready = checkNotNull(loaded)
        val selection = rememberGalleryPagerSelection(ready, viewModel.initialPageIn(media))
        val currentIndex = selection.activeIndexIn(ready)
        val waitingForRestoredPage = selection.activePageKey !in ready.pageKeys
        LaunchedEffect(selection.activePageKey, ready.threadMediaIndices) {
            ready.threadMediaIndexAt(currentIndex)?.let(mediaPageChanged)
        }

        // Exit and download remain visible while viewing or playing media.
        val closeLabel = stringResource(R.string.gallery_close)
        val downloadLabel = stringResource(R.string.gallery_download)
        var actionsFor by remember { mutableStateOf<MediaAttachment?>(null) }

        LaunchedEffect(selection.activePageKey, media, playback.preloadNextClip) {
            if (!waitingForRestoredPage) viewModel.prefetchAround(currentIndex, media)
        }
        val haptics = LocalHapticFeedback.current
        val close = {
            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            ready.threadMediaIndexAt(currentIndex)?.let(mediaPageChanged)
            onClose()
        }
        val save: (MediaAttachment) -> Unit = { attachment ->
            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            viewModel.download(attachment)
        }
        val closeThresholdPx = with(density) { CLOSE_PULL_THRESHOLD.toPx() }
        val currentType = media.getOrNull(currentIndex)?.type?.takeUnless { waitingForRestoredPage }
        val hasTransport = currentType == MediaType.VIDEO || currentType == MediaType.AUDIO
        val latestClose by rememberUpdatedState(close)
        val pullToClose = remember(closeThresholdPx) { PullPastEndToClose(closeThresholdPx) { latestClose() } }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    // Vertical paging and pull-past-end coexist with deliberate sideways close.
                    .nestedScroll(pullToClose)
                    .readerMediaSwipeToClose(close, toolbarHeight, if (hasTransport) 160.dp else 0.dp),
        ) {
            if (waitingForRestoredPage) NextLoading()
            if (!waitingForRestoredPage) {
                GalleryMediaPager(
                    content = ready,
                    selection = selection,
                    modifier = Modifier.fillMaxSize(),
                ) { item, isActive, isNear ->
                    var revealed by remember(item.id, item.isSpoiler) { mutableStateOf(!item.isSpoiler) }
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!revealed) {
                            Box(
                                modifier = Modifier.fillMaxSize().clickable { revealed = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.gallery_spoiler_reveal),
                                    color = Color.White,
                                    style = OrbinType.labelMedium,
                                )
                            }
                        } else if (item.type == MediaType.VIDEO || item.type == MediaType.AUDIO) {
                            if (isActive) {
                                // A neighbour or covered file must not allocate a decoder or fetch its source.
                                VideoPlayer(
                                    url = item.sourceUrl,
                                    modifier = Modifier.fillMaxSize(),
                                    autoPlay = playback.autoplay,
                                    muted = viewerMuted,
                                    loop = playback.loopVideos,
                                    active = true,
                                    onLongPress = { actionsFor = item },
                                    playbackSpeed = playbackSpeed,
                                    onDoubleTapSave = { save(item) },
                                )
                            } else {
                                MediaThumbnail(
                                    attachment = item,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        } else {
                            ZoomableImage(
                                url = item.sourceUrl,
                                contentDescription = item.originalFileName,
                                modifier = Modifier.fillMaxSize(),
                                placeholderUrl = item.thumbnailUrl,
                                active = isActive || (playback.preloadNextClip && isNear),
                                onLongPress = { actionsFor = item },
                                onDoubleTapSave = { save(item) },
                            )
                        }
                    }
                }
            }

            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged {
                    toolbarHeight = with(density) { it.height.toDp() }
                },
            ) {
                val currentItem = media[currentIndex]
                ReaderMediaToolbar(
                    filename =
                        if (waitingForRestoredPage) {
                            stringResource(
                                R.string.next_media_title,
                            )
                        } else {
                            currentItem.originalFileName
                        },
                    position = if (waitingForRestoredPage) "" else "${currentIndex + 1}/${media.size}",
                    closeLabel = closeLabel,
                    onClose = close,
                ) {
                    if (!waitingForRestoredPage &&
                        (currentItem.type == MediaType.VIDEO || currentItem.type == MediaType.AUDIO)
                    ) {
                        ReaderMediaAction(
                            imageVector =
                                if (viewerMuted) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                            label = stringResource(if (viewerMuted) R.string.gallery_unmute else R.string.gallery_mute),
                            onClick = {
                                viewerMuted = !viewerMuted
                                SessionAudio.muted = viewerMuted
                            },
                        )
                    }
                    if (!waitingForRestoredPage) {
                        ReaderMediaAction(
                            imageVector = Icons.Filled.FileDownload,
                            label = downloadLabel,
                            onClick = { save(currentItem) },
                        )
                        ReaderMediaAction(
                            imageVector = Icons.Filled.MoreVert,
                            label = stringResource(R.string.gallery_more_options),
                            onClick = { actionsFor = currentItem },
                            overflow = true,
                        )
                    }
                }
            }

            actionsFor?.let { attachment ->
                MediaActionsMenu(
                    attachment = attachment,
                    onSave = viewModel::download,
                    onDismiss = { actionsFor = null },
                    onSaveRemaining =
                        if (checkNotNull(loaded).threadMedia.size > 1) {
                            { viewModel.downloadRestOfThread(currentIndex) }
                        } else {
                            null
                        },
                    playbackSpeed =
                        playbackSpeed.takeIf {
                            attachment.type == MediaType.VIDEO ||
                                attachment.type == MediaType.AUDIO
                        },
                    onPlaybackSpeedChange = { playbackSpeed = it },
                    onCopyImage = { attachment ->
                        scope.launch {
                            viewModel.copyImage(attachment.sourceUrl)
                            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        }
                    },
                )
            }

            if (downloadState.isBusy) {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                            ).padding(top = toolbarHeight + 8.dp)
                            .fillMaxWidth(0.92f)
                            .nextElevatedSurface(RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = downloadState.label ?: stringResource(R.string.gallery_preparing_media),
                        color = next.ink,
                        style = OrbinType.footnote,
                    )
                    NextLinearProgress(progress = downloadState.progressValue)
                }
            }
        }
    }
}

/**
 * Closes the viewer when a drag keeps going past the first or last item.
 *
 * The pager is vertical, so an ordinary swipe down goes back an item; only the part of a drag the
 * pager cannot use — past either end — counts towards closing, and only once it is long enough to
 * be deliberate.
 */
private class PullPastEndToClose(
    private val thresholdPx: Float,
    private val onClose: () -> Unit,
) : NestedScrollConnection {
    private var pulled = 0f

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source == NestedScrollSource.UserInput) pulled += available.y
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (abs(pulled) >= thresholdPx) onClose()
        pulled = 0f
        return Velocity.Zero
    }
}

private val CLOSE_PULL_THRESHOLD = 96.dp
