package com.orbin.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.PlaybackSettings
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_media_close
import com.orbin.ios.resources.ios_media_download_rest
import com.orbin.ios.resources.ios_media_loop_off
import com.orbin.ios.resources.ios_media_loop_on
import com.orbin.ios.resources.ios_media_more_options
import com.orbin.ios.resources.ios_media_mute
import com.orbin.ios.resources.ios_media_not_saved
import com.orbin.ios.resources.ios_media_not_viewable
import com.orbin.ios.resources.ios_media_open_in_browser
import com.orbin.ios.resources.ios_media_save
import com.orbin.ios.resources.ios_media_saving
import com.orbin.ios.resources.ios_media_spoiler
import com.orbin.ios.resources.ios_media_spoiler_reveal
import com.orbin.ios.resources.ios_media_unmute
import com.orbin.uinext.ReaderMediaAction
import com.orbin.uinext.ReaderMediaSpeedOptions
import com.orbin.uinext.ReaderMediaToolbar
import com.orbin.uinext.readerMediaSwipeToClose
import org.jetbrains.compose.resources.stringResource
import kotlin.math.exp

internal object MediaViewerTags {
    const val PAGER = "ios-media-viewer-pager"
    const val ACTIVE_PAGE = "ios-media-viewer-active-page"
    const val PLAYER = "ios-media-viewer-player"
    const val PREVIEW = "ios-media-viewer-preview"
    const val EXTERNAL = "ios-media-viewer-external"
}

/**
 * The thread's files full screen, one per page: swipe vertically between files, pinch images to zoom,
 * double-tap to save, and swipe sideways or use Close to leave. Video and audio in a format the system
 * player handles play on their page ([NativePlayer]). WebM uses WebKit on iOS 17.4+; older
 * systems offer the file in the browser.
 */
@Composable
internal fun MediaViewer(
    files: List<MediaAttachment>,
    startIndex: Int,
    onClose: () -> Unit,
    onSave: (MediaAttachment) -> Boolean = { false },
    onPageChanged: (Int) -> Unit = {},
    playback: PlaybackSettings = PlaybackSettings(),
    onDownloadRest: ((Int) -> Unit)? = null,
) {
    if (files.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val selection = rememberMediaViewerSelection(files, startIndex, onPageChanged)
    val pager = selection.pager
    val preloader = remember { createNativeMediaPreloader() }
    DisposableEffect(preloader) {
        onDispose { preloader.close() }
    }
    val close = {
        onPageChanged(selection.index)
        onClose()
    }
    var pencilMenu by remember { mutableStateOf(false) }
    // Apple Pencil: a double-tap moves to the next file, and a squeeze offers save and close.
    OnPencilGesture { gesture ->
        when (gesture) {
            PencilGesture.DOUBLE_TAP ->
                if (selection.index < files.lastIndex) pager.animateScrollToPage(selection.index + 1)
            PencilGesture.SQUEEZE -> pencilMenu = true
        }
    }
    val current = files[selection.index]
    var muted by remember(playback.startMuted) { mutableStateOf(playback.startMuted && SessionAudio.muted) }
    var playbackSpeed by rememberSaveable { mutableFloatStateOf(1f) }
    val saveResults = remember { mutableStateMapOf<String, Boolean>() }

    fun saveFile(
        file: MediaAttachment,
        key: String,
    ) {
        if (saveResults[key] == null) saveResults[key] = onSave(file)
    }
    val loopOverrides = remember { mutableStateMapOf<String, Boolean>() }
    val revealedFiles = remember { mutableStateMapOf<String, Boolean>() }
    val playbackReady = remember { mutableStateMapOf<String, Boolean>() }
    val currentRevealKey = "${selection.key}:${current.isSpoiler}"
    val currentRevealed = revealedFiles[currentRevealKey] ?: !current.isSpoiler
    val currentLoop = loopOverrides[selection.key] ?: playback.loopVideos
    val currentPlayable = current.playsInApp || (current.isWebM && supportsWebM)
    val currentReady = playbackReady[selection.key] == true
    LaunchedEffect(preloader, files, selection.index, playback.preloadNextClip, currentReady) {
        prepareUpcomingAfterSettle(
            nextMediaPreloadTarget(
                files,
                selection.index,
                playback.preloadNextClip,
                supportsWebM,
                currentReady = currentReady,
            ),
            prepare = preloader::prepare,
        )
    }
    val density = LocalDensity.current
    var toolbarHeight by remember { mutableStateOf(56.dp) }
    val uriHandler = LocalUriHandler.current
    var moreMenu by remember(selection.key) { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Color.Black,
            ).readerMediaSwipeToClose(close, toolbarHeight, if (currentPlayable) 160.dp else 0.dp),
    ) {
        NativeMediaPreloadHost(preloader)
        // Files stack top to bottom, as in the Android viewer: swipe up for the next one.
        VerticalPager(
            state = pager,
            key = { page -> selection.keys.getOrNull(page) ?: "media-pending:$page" },
            beyondViewportPageCount = if (playback.preloadNextClip) 1 else 0,
            modifier = Modifier.fillMaxSize().testTag(MediaViewerTags.PAGER),
        ) { page ->
            val file = files.getOrNull(page) ?: return@VerticalPager
            val active = selection.keys.getOrNull(page) == selection.key
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (active) {
                            Modifier.testTag(MediaViewerTags.ACTIVE_PAGE).semantics {
                                contentDescription = file.originalFileName
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                val fileKey = selection.keys[page]
                val revealKey = "$fileKey:${file.isSpoiler}"
                MediaPage(
                    file,
                    active = active,
                    playback = playback,
                    preloader = preloader,
                    revealed = revealedFiles[revealKey] ?: !file.isSpoiler,
                    onReveal = { revealedFiles[revealKey] = true },
                    looping = loopOverrides[fileKey] ?: playback.loopVideos,
                    muted = muted,
                    onMuteChange = { muted = it },
                    playbackSpeed = playbackSpeed,
                    onDoubleTapSave = if (active) ({ saveFile(file, fileKey) }) else null,
                    onPlaybackReadyChange = { playbackReady[fileKey] = it },
                )
            }
        }
        ReaderMediaToolbar(
            filename =
                if (currentRevealed) {
                    current.originalFileName
                } else {
                    stringResource(Res.string.ios_media_spoiler)
                },
            position = "${selection.index + 1}/${files.size}",
            closeLabel = stringResource(Res.string.ios_media_close),
            onClose = close,
            modifier = Modifier.onSizeChanged { toolbarHeight = with(density) { it.height.toDp() } },
        ) {
            if (currentPlayable && currentRevealed) {
                ReaderMediaAction(
                    imageVector =
                        if (muted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                    label = stringResource(if (muted) Res.string.ios_media_unmute else Res.string.ios_media_mute),
                    onClick = { muted = SessionAudio.toggle(muted) },
                )
            }
            SaveButton(saveResults[selection.key]) { saveFile(current, selection.key) }
            Box {
                ReaderMediaAction(
                    imageVector = Icons.Filled.MoreVert,
                    label = stringResource(Res.string.ios_media_more_options),
                    onClick = { moreMenu = true },
                    overflow = true,
                )
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    val external = safeExternalLink(current.sourceUrl)
                    if (external != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.ios_media_open_in_browser)) },
                            onClick = {
                                moreMenu = false
                                uriHandler.openUri(external)
                            },
                        )
                    }
                    onDownloadRest?.let { downloadRest ->
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.ios_media_download_rest)) },
                            onClick = {
                                moreMenu = false
                                downloadRest(selection.index)
                            },
                        )
                    }
                    if (currentPlayable && currentRevealed) {
                        ReaderMediaSpeedOptions(playbackSpeed) { speed ->
                            playbackSpeed = speed
                            moreMenu = false
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (currentLoop) {
                                            Res.string.ios_media_loop_on
                                        } else {
                                            Res.string.ios_media_loop_off
                                        },
                                    ),
                                )
                            },
                            onClick = {
                                moreMenu = false
                                loopOverrides[selection.key] = !currentLoop
                            },
                        )
                    }
                }
            }
        }
        PencilMenu(
            expanded = pencilMenu,
            onDismiss = { pencilMenu = false },
            actions =
                listOf<Pair<String, () -> Unit>>(
                    stringResource(Res.string.ios_media_save) to { saveFile(current, selection.key) },
                    stringResource(Res.string.ios_media_close) to close,
                ),
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/**
 * Saves the file on screen. Once pressed it says so for that file, and says when a file cannot be
 * saved at all (not https, or caught by the permanent filter); the Downloads tab has the rest.
 */
@Composable
private fun SaveButton(
    result: Boolean?,
    onSave: () -> Unit,
) {
    ReaderMediaAction(
        imageVector = Icons.Filled.FileDownload,
        label =
            stringResource(
                when (result) {
                    null -> Res.string.ios_media_save
                    true -> Res.string.ios_media_saving
                    false -> Res.string.ios_media_not_saved
                },
            ),
        onClick = onSave,
        enabled = result == null,
    )
}

@Composable
private fun MediaPage(
    file: MediaAttachment,
    active: Boolean,
    playback: PlaybackSettings,
    preloader: NativeMediaPreloader,
    revealed: Boolean,
    onReveal: () -> Unit,
    looping: Boolean,
    muted: Boolean,
    onMuteChange: (Boolean) -> Unit,
    playbackSpeed: Float,
    onDoubleTapSave: (() -> Unit)?,
    onPlaybackReadyChange: (Boolean) -> Unit,
) {
    val playable = remember(file) { file.takeIf { it.playsInApp }?.let { safeExternalLink(it.sourceUrl) } }
    val webm = remember(file) { file.takeIf { it.isWebM }?.let { safeExternalLink(it.sourceUrl) } }
    // A spoilered file (a spoiler, or one the violent-media cover marked) waits behind the cover
    // until tapped, and nothing of it plays before then.
    if (!revealed) {
        SpoilerCover(
            modifier = Modifier.clickable(onClick = onReveal),
            text = stringResource(Res.string.ios_media_spoiler_reveal),
        )
        return
    }
    when {
        file.type == MediaType.IMAGE || file.type == MediaType.ANIMATED_IMAGE ->
            ZoomableImage(
                file,
                active,
                onDoubleTapSave,
            )
        // Only the visible page owns a native player; pager neighbours need no decoder.
        playable != null && !active -> MediaPoster(file)
        webm != null && supportsWebM && !active -> MediaPoster(file)
        playable != null ->
            NativePlayer(
                url = playable,
                active = active,
                loop = looping,
                autoPlay = playback.autoplay,
                initiallyMuted = muted,
                onMuteIntentChange = onMuteChange,
                playbackSpeed = playbackSpeed,
                onDoubleTapSave = onDoubleTapSave,
                preloader = preloader,
                modifier = Modifier.fillMaxSize().safeDrawingPadding().testTag(MediaViewerTags.PLAYER),
                onPlaybackReadyChange = onPlaybackReadyChange,
            )
        webm != null && supportsWebM ->
            NativeWebMPlayer(
                url = webm,
                modifier = Modifier.fillMaxSize().safeDrawingPadding().testTag(MediaViewerTags.PLAYER),
                active = active,
                autoPlay = playback.autoplay,
                initiallyMuted = muted,
                onMuteIntentChange = onMuteChange,
                playbackSpeed = playbackSpeed,
                onDoubleTapSave = onDoubleTapSave,
                loop = looping,
                preloader = preloader,
                onPlaybackReadyChange = onPlaybackReadyChange,
            )
        else -> ExternalFile(file)
    }
}

/** An inactive supported clip is a preview, never an unsupported-format browser fallback. */
@Composable
private fun MediaPoster(file: MediaAttachment) {
    val preview = remember(file.thumbnailUrl) { safeExternalLink(file.thumbnailUrl) }
    Box(Modifier.fillMaxSize().testTag(MediaViewerTags.PREVIEW), contentAlignment = Alignment.Center) {
        if (preview != null) {
            AsyncImage(
                model = preview,
                contentDescription = file.originalFileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ZoomableImage(
    file: MediaAttachment,
    active: Boolean,
    onDoubleTapSave: (() -> Unit)?,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Turning an Apple Pencil Pro's barrel while it hovers zooms the image on screen.
    if (active) {
        OnPencilRoll { turn ->
            scale = (scale * exp(turn)).coerceIn(1f, MAX_SCALE)
            offset = zoomedOffset(offset, scale, size)
        }
    }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { size = it }
                .pointerInput(onDoubleTapSave) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (onDoubleTapSave != null) {
                                onDoubleTapSave()
                            } else {
                                scale = if (scale > 1f) 1f else DOUBLE_TAP_SCALE
                                offset = Offset.Zero
                            }
                        },
                    )
                }.pointerInput(Unit) {
                    // Only a pinch, or any drag once zoomed in, belongs to the image. A one-finger
                    // swipe at normal size is left unconsumed so the pager turns the page.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val pinching = event.changes.count { it.pressed } > 1
                            if (pinching || scale > 1f) {
                                scale = (scale * event.calculateZoom()).coerceIn(1f, MAX_SCALE)
                                offset = zoomedOffset(offset + event.calculatePan(), scale, size)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        ) {
            // The thumbnail is already cached from the thread, so something shows at once; the
            // full image draws over it when it arrives.
            AsyncImage(
                model = file.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            AsyncImage(
                model = file.sourceUrl,
                contentDescription = file.originalFileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ExternalFile(file: MediaAttachment) {
    val uriHandler = LocalUriHandler.current
    val link = remember(file) { safeExternalLink(file.sourceUrl) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag(MediaViewerTags.EXTERNAL),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = file.thumbnailUrl,
            contentDescription = file.originalFileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
        )
        Text(
            text = stringResource(Res.string.ios_media_not_viewable),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (link != null) {
            TextButton(onClick = { uriHandler.openUri(link) }) {
                Text(stringResource(Res.string.ios_media_open_in_browser))
            }
        }
    }
}

/**
 * Where a zoomed image may sit: [offset] kept within the overflow that [scale] creates, so the
 * image edge never pulls in past the screen edge, and centred again at normal size.
 */
internal fun zoomedOffset(
    offset: Offset,
    scale: Float,
    size: IntSize,
): Offset {
    if (scale <= 1f) return Offset.Zero
    val maxX = size.width * (scale - 1f) / 2f
    val maxY = size.height * (scale - 1f) / 2f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
