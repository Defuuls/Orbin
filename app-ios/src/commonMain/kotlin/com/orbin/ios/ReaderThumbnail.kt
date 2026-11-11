package com.orbin.ios

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.SizeResolver
import com.orbin.core.model.MediaAttachment
import com.orbin.uinext.ReaderSpoilerCover

/**
 * A catalog preview never requests originals, GIF playback or a video frame. The parent's fixed
 * square reserves layout space, and Coil resolves its pixel constraints at the screen density.
 */
@Composable
internal fun ReaderThumbnail(
    attachment: MediaAttachment,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    var revealed by rememberSaveable(attachment.id, attachment.thumbnailUrl) { mutableStateOf(false) }
    PencilHoverPreview(
        attachment = if (revealed) attachment.copy(isSpoiler = false) else attachment,
        modifier = modifier,
        thumbnailOnly = true,
    ) {
        if (attachment.isSpoiler && !revealed) {
            ReaderSpoilerCover(onReveal = { revealed = true }, modifier = Modifier.fillMaxSize())
        } else {
            val context = LocalPlatformContext.current
            val sizeResolver = rememberConstraintsSizeResolver()
            val request =
                remember(context, attachment.thumbnailUrl, sizeResolver) {
                    readerThumbnailRequest(context, attachment, sizeResolver)
                }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(sizeResolver),
            )
        }
    }
}

/** No source fallback: a missing provider preview leaves the already reserved square empty. */
internal fun readerThumbnailRequest(
    context: PlatformContext,
    attachment: MediaAttachment,
    sizeResolver: SizeResolver,
): ImageRequest =
    ImageRequest
        .Builder(context)
        .data(attachment.thumbnailUrl.takeIf(String::isNotBlank))
        .size(sizeResolver)
        .precision(Precision.EXACT)
        .build()
