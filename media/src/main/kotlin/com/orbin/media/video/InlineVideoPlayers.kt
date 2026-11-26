package com.orbin.media.video

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import com.orbin.uinext.components.InlinePlayerPool
import com.orbin.uinext.components.rememberInlinePlayerPool

internal val MovieAudio: AudioAttributes =
    AudioAttributes
        .Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .setUsage(C.USAGE_MEDIA)
        .build()

@Suppress("UnsafeOptInUsageError")
internal fun buildVideoPlayer(
    context: Context,
    mediaSourceFactory: DefaultMediaSourceFactory,
    muted: Boolean,
): ExoPlayer =
    ExoPlayer
        .Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .setAudioAttributes(MovieAudio, !muted)
        .build()

/**
 * The ExoPlayers a thread screen's inline videos share, released when the screen leaves.
 *
 * [VideoPlayer] takes one when a tile starts playing and gives it back when the tile stops, so
 * scrolling from video to video reuses a player instead of building and releasing one each time.
 */
@Suppress("UnsafeOptInUsageError")
@Composable
fun rememberInlineVideoPlayerPool(key: Any?): InlinePlayerPool<ExoPlayer> {
    val appContext = LocalContext.current.applicationContext
    val mediaSourceFactory =
        remember(appContext) {
            DefaultMediaSourceFactory(appContext.videoMediaDataSourceFactory(), DefaultExtractorsFactory())
        }
    return rememberInlinePlayerPool(
        key = key,
        create = { buildVideoPlayer(appContext, mediaSourceFactory, muted = true) },
        reset = ::resetForReuse,
        release = { runCatching { it.release() } },
    )
}

/** Back to a new player's state: nothing loaded, no surface, and default playback settings. */
internal fun resetForReuse(player: ExoPlayer) {
    player.stop()
    player.clearMediaItems()
    player.clearVideoSurface()
    player.playWhenReady = false
    player.repeatMode = Player.REPEAT_MODE_OFF
    player.volume = 1f
    player.setPlaybackSpeed(1f)
}
