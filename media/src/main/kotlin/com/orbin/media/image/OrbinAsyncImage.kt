package com.orbin.media.image

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.media.R

/**
 * Thin wrapper over Coil's [AsyncImage] with visible failure state and request diagnostics.
 *
 * [placeholderUrl] names a cheaper image to paint underneath while [url] loads. Without it a cell
 * showing a full-resolution source stays blank for the whole fetch, which is a slower first paint
 * than the low-resolution thumbnail it replaced — noticeably so on a poor connection. The
 * placeholder is dropped once the real image succeeds, so nothing is retained behind it.
 */
@Composable
fun OrbinAsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderUrl: String? = null,
) {
    // remember(url) already resets these when the URL changes; no effect needed.
    var loadFailed by remember(url) { mutableStateOf(false) }
    var failureMessage by remember(url) { mutableStateOf<String?>(null) }
    var loaded by remember(url) { mutableStateOf(false) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (placeholderUrl != null && placeholderUrl != url && !loaded) {
            AsyncImage(
                model = placeholderUrl,
                // Described by the image drawn over it; announcing both would duplicate it.
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }

        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            onSuccess = {
                loadFailed = false
                failureMessage = null
                loaded = true
            },
            onError = { state ->
                val throwable = state.result.throwable
                Log.w(TAG, "Image failed to load", throwable)
                loadFailed = true
                failureMessage = throwable.mediaLoadMessage()
            },
        )

        if (loadFailed) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = ERROR_OVERLAY_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.BrokenImage,
                    contentDescription = failureMessage ?: "Image unavailable",
                    tint = Color.White,
                )
                Text(
                    text = failureMessage ?: "Image unavailable",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * A post thumbnail: shows the attachment's thumbnail, a play badge for video/audio, and a
 * blur-style overlay for spoilers. Tapping invokes [onClick] (open full media / gallery).
 *
 * Sized entirely by [modifier] (defaults to the classic 120dp square) so callers can render it
 * as a fixed size or have it fill its container, e.g. a full-width grid cell.
 */
@Composable
fun MediaThumbnail(
    attachment: MediaAttachment,
    modifier: Modifier = Modifier,
    fullResolution: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop,
    onClick: () -> Unit = {},
) {
    val finalModifier = if (modifier == Modifier) modifier.size(120.dp) else modifier
    // Provider thumbnails are only ~250px wide, so they look soft in the larger layouts. Those
    // callers pass [fullResolution] to pull the original file instead; AsyncImage sizes the
    // request from the layout constraints, so the bitmap is still downsampled to the cell and
    // memory stays bounded by display size. Small/many-tile layouts keep the cheap thumbnail —
    // fetching full originals for dozens of concurrently-visible tiles costs more in network and
    // decode contention than the extra sharpness is worth.
    val imageUrl =
        if (fullResolution && attachment.type == MediaType.IMAGE && attachment.sourceUrl.isNotBlank()) {
            attachment.sourceUrl
        } else {
            attachment.thumbnailUrl
        }

    Box(
        modifier =
            finalModifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        OrbinAsyncImage(
            url = imageUrl,
            contentDescription = attachment.originalFileName,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            // Only meaningful when imageUrl is the full-resolution source; otherwise it is the
            // same URL and the placeholder is skipped.
            placeholderUrl = attachment.thumbnailUrl,
        )

        if (attachment.isSpoiler) {
            SpoilerOverlay()
        } else if (attachment.type == MediaType.VIDEO || attachment.type == MediaType.AUDIO) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.media_play),
                tint = Color.White,
                modifier =
                    Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .size(36.dp),
            )
        }
    }
}

/**
 * The blackout drawn over a spoilered attachment.
 *
 * Every surface that paints a thumbnail has to draw this, and each one that wrote its own drew it
 * a little differently — one of them at a lighter alpha, and one not at all, which showed the file
 * in the clear. It is one composable now: a surface either draws it or visibly does not.
 */
@Composable
fun SpoilerOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = SPOILER_SCRIM_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.VisibilityOff,
            contentDescription = stringResource(R.string.media_spoiler),
            tint = Color.White,
        )
    }
}

// Opaque enough that shape and colour do not read through it. A spoiler that can be guessed from
// its own blackout has not been hidden.
private const val SPOILER_SCRIM_ALPHA = 0.85f

private fun Throwable.mediaLoadMessage(): String =
    if (hasHttpStatus(HTTP_TOO_MANY_REQUESTS)) {
        "Image rate limited"
    } else {
        "Image unavailable"
    }

private fun Throwable.hasHttpStatus(statusCode: Int): Boolean =
    generateSequence(this as Throwable?) { it.cause }
        .any { throwable -> throwable.message?.contains(statusCode.toString()) == true }

private const val TAG = "OrbinAsyncImage"
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val ERROR_OVERLAY_ALPHA = 0.62f
