package com.orbin.feature.gallery

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
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
import com.orbin.media.image.ImageCopyResult
import com.orbin.media.image.MediaThumbnail
import com.orbin.media.image.ZoomableImage
import com.orbin.media.video.SessionAudio
import com.orbin.media.video.VideoPlayer
import com.orbin.uinext.InlineAction
import com.orbin.uinext.NextEmpty
import com.orbin.uinext.NextError
import com.orbin.uinext.NextLinearProgress
import com.orbin.uinext.NextLoading
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ReaderMediaAction
import com.orbin.uinext.ReaderMediaBack
import com.orbin.uinext.ReaderMediaTitle
import com.orbin.uinext.ReaderMediaToolbar
import com.orbin.uinext.next
import com.orbin.uinext.nextElevatedSurface
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType
import com.orbin.uinext.tokens.ReaderSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val threadMediaIndices = loaded?.threadMediaIndices.orEmpty()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playback = remember(settings) { viewModel.playbackSettings() }
    var viewerMuted by remember(playback.startMuted) {
        mutableStateOf(playback.startMuted && SessionAudio.muted)
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaPageChanged by rememberUpdatedState(onMediaPageChanged)

    NextTheme(darkTheme = true) {
        if (media.isEmpty()) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                when (val state = content) {
                    GalleryContentState.Loading -> NextLoading()
                    is GalleryContentState.Error -> NextError(state.message, onRetry = viewModel::retry)
                    is GalleryContentState.Ready ->
                        NextEmpty(
                            stringResource(
                                if (viewModel.videosOnly) R.string.gallery_no_thread_videos else R.string.gallery_empty,
                            ),
                            Modifier.fillMaxSize(),
                        )
                }
                InlineAction(
                    label = stringResource(R.string.gallery_close),
                    onClick = onClose,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                                ),
                            ).padding(horizontal = NextSpace.gutter, vertical = 8.dp),
                )
            }
            return@NextTheme
        }

        val pagerState =
            rememberPagerState(
                initialPage = viewModel.initialPageIn(media).coerceIn(0, media.lastIndex),
                pageCount = { media.size },
            )

        LaunchedEffect(pagerState, threadMediaIndices) {
            snapshotFlow { pagerState.settledPage }
                .distinctUntilChanged()
                .collect { page -> threadMediaIndices.getOrNull(page)?.let(mediaPageChanged) }
        }

        // Top bar auto-hides after a period of inactivity for uninterrupted media viewing,
        // and reveals upon tapping media or switching pages.
        var chromeVisible by remember { mutableStateOf(true) }

        // Hide the gallery chrome while a video plays in fullscreen; reset on every page change so a
        // swipe away always brings the top bar back.
        var videoFullscreen by remember { mutableStateOf(false) }
        LaunchedEffect(pagerState.settledPage) {
            videoFullscreen = false
            chromeVisible = true
        }

        val closeLabel = stringResource(R.string.gallery_close)
        val downloadLabel = stringResource(R.string.gallery_download)
        val imageCopiedMessage = stringResource(R.string.gallery_image_copied)
        val urlCopiedMessage = stringResource(R.string.gallery_url_copied)
        var actionsFor by remember { mutableStateOf<MediaAttachment?>(null) }

        LaunchedEffect(chromeVisible, pagerState.settledPage, actionsFor != null, downloadState.isBusy) {
            if (chromeVisible && actionsFor == null && !downloadState.isBusy) {
                delay(AUTO_HIDE_DELAY_MS)
                chromeVisible = false
            }
        }

        LaunchedEffect(pagerState.settledPage, media, playback.preloadNextClip) {
            viewModel.prefetchAround(pagerState.settledPage, media)
        }
        val haptics = LocalHapticFeedback.current
        val close = {
            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            threadMediaIndices.getOrNull(pagerState.settledPage)?.let(mediaPageChanged)
            onClose()
        }
        val save: (MediaAttachment) -> Unit = { attachment ->
            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            viewModel.download(attachment)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, R.string.gallery_saving, Toast.LENGTH_SHORT).show()
            }
        }
        val closeThresholdPx = with(LocalDensity.current) { CLOSE_PULL_THRESHOLD.toPx() }
        val pullToClose = remember(closeThresholdPx) { PullPastEndToClose(closeThresholdPx) { close() } }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    // Pulling past the first or last item closes the viewer.
                    .nestedScroll(pullToClose),
        ) {
            VerticalPager(
                state = pagerState,
                key = { page -> checkNotNull(loaded).pageKey(page) },
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
            ) { page ->
                val item = media[page.coerceIn(0, media.lastIndex)]
                val isActive = page == pagerState.settledPage
                val isNear = kotlin.math.abs(page - pagerState.settledPage) <= 1
                var revealed by remember(item.id) { mutableStateOf(!item.isSpoiler) }
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
                                style = NextType.labelMedium,
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
                                onFullscreenChange = { videoFullscreen = it },
                                onControlsToggle = { chromeVisible = it },
                                onLongPress = { actionsFor = item },
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
                            onTap = { chromeVisible = !chromeVisible },
                            onLongPress = { actionsFor = item },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = chromeVisible && !videoFullscreen,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            ) {
                ReaderMediaToolbar {
                    ReaderMediaBack(closeLabel, close)
                    val currentIndex = pagerState.settledPage.coerceIn(0, media.lastIndex)
                    val currentItem = media[currentIndex]
                    ReaderMediaTitle(
                        filename = currentItem.originalFileName,
                        position = "${currentIndex + 1}/${media.size}",
                        modifier = Modifier.weight(1f),
                    )
                    if (currentItem.type == MediaType.VIDEO || currentItem.type == MediaType.AUDIO) {
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

            actionsFor?.let { attachment ->
                MediaActionsMenu(
                    attachment = attachment,
                    onSave = viewModel::download,
                    onDismiss = { actionsFor = null },
                    onCopyImage = { attachment ->
                        scope.launch {
                            val result = viewModel.copyImage(attachment.sourceUrl)
                            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                val message =
                                    when (result) {
                                        ImageCopyResult.IMAGE -> imageCopiedMessage
                                        ImageCopyResult.URL -> urlCopiedMessage
                                    }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
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
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                            ).padding(top = ReaderSize.appBar + 8.dp)
                            .fillMaxWidth(0.92f)
                            .nextElevatedSurface(RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = downloadState.label ?: stringResource(R.string.gallery_preparing_media),
                        color = next.ink,
                        style = NextType.footnote,
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
private const val AUTO_HIDE_DELAY_MS = 3_000L
