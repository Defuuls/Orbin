package com.orbin.media.video

import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.orbin.media.R

/**
 * A video playing silently on a loop where its thumbnail was, like a GIF.
 *
 * Uses [MediaVideo] (backed by the bounded [VideoPlayers] pool and [VideoServer] cache) and a
 * `TextureView`-backed [PlayerView] so scrolling in Compose lists stays smooth without
 * `SurfaceView` hole-punching jank or main-thread player allocation spikes.
 */
@Suppress("UnsafeOptInUsageError")
@Composable
fun InlineLoop(
    url: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val context = LocalContext.current
    val currentActive by rememberUpdatedState(active)
    val video = remember(url) { MediaVideo.obtain(source = url, muted = true) }
    val owner = remember(video) { Any() }
    val player by video.player.collectAsState()
    val hasFirstFrame by video.hasFirstFrame.collectAsState()

    LaunchedEffect(video, active) {
        if (active) {
            video.want(context = context, owner = owner, audible = false, shownFraction = 1f)
        } else {
            video.unwant(owner)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, video) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> video.unwant(owner)
                    Lifecycle.Event.ON_START ->
                        if (currentActive) {
                            video.want(context = context, owner = owner, audible = false, shownFraction = 1f)
                        }
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(video) {
        onDispose {
            video.release(owner)
        }
    }

    AndroidView(
        modifier =
            modifier.graphicsLayer {
                alpha = if (player != null && hasFirstFrame) 1f else 0f
            },
        factory = { viewContext ->
            (LayoutInflater.from(viewContext).inflate(R.layout.media_texture_player_view, null, false) as PlayerView)
                .apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    isClickable = false
                    isFocusable = false
                }
        },
        update = { view -> view.player = player },
        onRelease = { view -> view.player = null },
    )
}
