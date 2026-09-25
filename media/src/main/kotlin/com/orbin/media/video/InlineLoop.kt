package com.orbin.media.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * A video playing silently on a loop where its thumbnail was, like a GIF.
 *
 * No controls and no touch handling of its own, so a tap still reaches whatever the tile opens.
 * Callers keep this to the one tile in view: each instance owns a player, released when it leaves
 * composition.
 */
@Suppress("UnsafeOptInUsageError")
@Composable
fun InlineLoop(
    url: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player =
        remember(url) {
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(
                        context.applicationContext.videoMediaDataSourceFactory(),
                        DefaultExtractorsFactory(),
                    ),
                ).build()
                .apply {
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    playWhenReady = true
                }
        }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                isClickable = false
                isFocusable = false
            }
        },
        update = { view -> view.player = player },
    )
}
