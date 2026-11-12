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
 * A catalog preview never requests GIF playback or a video frame. The parent reserves the layout
 * space, and Coil resolves its pixel constraints at the screen density.
 *
 * A list cell is thumbnail-sized, so it only ever requests the provider thumbnail. A grid card is
 * far wider than that thumbnail (about 250 pixels), so it passes [sharp]: the thumbnail fills the
 * space at once and, for a still image, the original is decoded to the card's size over it.
 */
@Composable
internal fun ReaderThumbnail(
    attachment: MediaAttachment,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    sharp: Boolean = false,
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
            val sharpUrl = attachment.cardImageUrl().takeIf { sharp && it != attachment.thumbnailUrl }
            if (sharpUrl != null) {
                val sharpRequest =
                    remember(context, sharpUrl, sizeResolver) {
                        readerThumbnailRequest(context, attachment, sizeResolver, sharpUrl)
                    }
                AsyncImage(
                    model = sharpRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * A request for [url], the provider thumbnail unless a grid card asks for its sharper image, decoded
 * to the measured cell. A blank [url] has no fallback: the already reserved space stays empty.
 */
internal fun readerThumbnailRequest(
    context: PlatformContext,
    attachment: MediaAttachment,
    sizeResolver: SizeResolver,
    url: String = attachment.thumbnailUrl,
): ImageRequest =
    ImageRequest
        .Builder(context)
        .data(url.takeIf(String::isNotBlank))
        .size(sizeResolver)
        .precision(Precision.EXACT)
        .build()
