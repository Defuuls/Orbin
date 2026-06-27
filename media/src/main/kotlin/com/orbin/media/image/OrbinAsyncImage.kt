package com.orbin.media.image

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType

/** Thin wrapper over Coil's [AsyncImage] with the app's default content scale. */
@Composable
fun OrbinAsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
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
