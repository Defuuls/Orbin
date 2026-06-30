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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType

/** Thin wrapper over Coil's [AsyncImage] with visible failure state and request diagnostics. */
@Composable
fun OrbinAsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var loadFailed by remember(url) { mutableStateOf(false) }
    var failureMessage by remember(url) { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        loadFailed = false
        failureMessage = null
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            onSuccess = {
                loadFailed = false
                failureMessage = null
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
 */
@Composable
fun MediaThumbnail(
    attachment: MediaAttachment,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        OrbinAsyncImage(
            url = attachment.thumbnailUrl,
            contentDescription = attachment.originalFileName,
            modifier = Modifier.size(120.dp),
        )

        if (attachment.isSpoiler) {
            Box(modifier = Modifier.size(120.dp).background(Color.Black.copy(alpha = 0.85f)))
            Icon(
                imageVector = Icons.Filled.VisibilityOff,
                contentDescription = "Spoiler",
                tint = Color.White,
            )
        } else if (attachment.type == MediaType.VIDEO || attachment.type == MediaType.AUDIO) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier =
                    Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .size(36.dp),
            )
        }
    }
}

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
