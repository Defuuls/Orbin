package com.orbin.media.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.PlayerView
import com.orbin.network.NetworkConfig

/**
 * A Media3/ExoPlayer-backed video player. The player is created per [url], loops by default, and
 * is released when the composable leaves composition so there are no leaked players. Autoplay and
 * the initial mute state are driven from settings by the caller; an in-player toggle lets the user
 * force mute/unmute for the clip they are watching.
 */
@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    muted: Boolean = true,
    active: Boolean = true,
) {
    val context = LocalContext.current
    // Per-clip mute override; resets to the [muted] default when the clip (url) changes.
    var isMuted by rememberSaveable(url) { mutableStateOf(muted) }
    var isBuffering by remember { mutableStateOf(false) }

    val httpDataSourceFactory =
        remember(context) {
            DefaultHttpDataSource.Factory().apply {
                setUserAgent(NetworkConfig.DEFAULT_USER_AGENT)
                setAllowCrossProtocolRedirects(true)
                setConnectTimeoutMs(15_000)
                setReadTimeoutMs(30_000)
            }
        }

    val mediaSourceFactory =
        remember(context, httpDataSourceFactory) {
            DefaultMediaSourceFactory(httpDataSourceFactory, DefaultExtractorsFactory())
        }

    val exoPlayer =
        remember(url, context, mediaSourceFactory) {
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    volume = if (muted) 0f else 1f
                    playWhenReady = active && autoPlay
                }
        }

    LaunchedEffect(url) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.seekTo(0)
        exoPlayer.playWhenReady = active && autoPlay
    }

    // Pause as soon as this page is no longer the active one so its audio never plays over the
    // next video; the active page autoplays when enabled.
    LaunchedEffect(active, autoPlay) {
        exoPlayer.playWhenReady = active && autoPlay
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(exoPlayer) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                }
            }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.useController = false
            },
        )
        if (isBuffering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        FilledTonalIconButton(
            onClick = { isMuted = !isMuted },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            Icon(
                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
            )
        }
    }
}
