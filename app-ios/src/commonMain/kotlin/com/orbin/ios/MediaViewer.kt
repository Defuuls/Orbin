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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_media_close
import com.orbin.ios.resources.ios_media_not_viewable
import com.orbin.ios.resources.ios_media_open_in_browser
import com.orbin.ios.resources.ios_media_position
import com.orbin.ios.resources.ios_media_spoiler_reveal
import org.jetbrains.compose.resources.stringResource

/**
 * The thread's files full screen, one per page: swipe between them, pinch or double-tap to zoom an
 * image, back (the edge swipe) or the close button to leave. Video and audio in a format the system
 * player handles play on their page ([NativePlayer]); the rest, WebM above all, show the thumbnail
 * and open in the browser.
 */
@Composable
internal fun MediaViewer(
    files: List<MediaAttachment>,
    startIndex: Int,
    onClose: () -> Unit,
) {
    if (files.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, files.lastIndex)) { files.size }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
            MediaPage(files[page], active = page == pager.currentPage)
        }
        Row(
            modifier = Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.ios_media_position, pager.currentPage + 1, files.size),
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp),
            )
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.ios_media_close),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun MediaPage(
    file: MediaAttachment,
    active: Boolean,
) {
    val playable = remember(file) { file.takeIf { it.playsInApp }?.let { safeExternalLink(it.sourceUrl) } }
    // A spoilered file (a spoiler, or one the violent-media cover marked) waits behind the cover
    // until tapped, and nothing of it plays before then.
    var revealed by remember(file) { mutableStateOf(!file.isSpoiler) }
    if (!revealed) {
        SpoilerCover(
            modifier = Modifier.clickable { revealed = true },
            text = stringResource(Res.string.ios_media_spoiler_reveal),
        )
        return
    }
    when {
        file.type == MediaType.IMAGE || file.type == MediaType.ANIMATED_IMAGE -> ZoomableImage(file)
        // Below the top bar, so the player's own controls never sit under the close button.
        playable != null ->
            NativePlayer(playable, active, Modifier.fillMaxSize().safeDrawingPadding().padding(top = PLAYER_TOP_INSET))
        else -> ExternalFile(file)
    }
}

@Composable
private fun ZoomableImage(file: MediaAttachment) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else DOUBLE_TAP_SCALE
                            offset = Offset.Zero
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
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
private val PLAYER_TOP_INSET = 48.dp
private const val DOUBLE_TAP_SCALE = 2.5f
