package com.orbin.media.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.PlayerView
import com.orbin.media.di.VideoMediaDataSource
import com.orbin.network.interceptor.RetryAfterTracker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * A Media3/ExoPlayer-backed video player. The player is created per [url], loops by default, and
 * is released when the composable leaves composition so there are no leaked players. Autoplay and
 * the initial mute state are driven from settings by the caller; tapping the video reveals compact
 * controls without permanently covering playing media.
 */
@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    muted: Boolean = true,
    active: Boolean = true,
    fullscreenByDefault: Boolean = false,
    autoRotate: Boolean = false,
    onFullscreenChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val uriHandler = LocalUriHandler.current
    val activity = remember(context) { context.findActivity() }
    var videoIsLandscape by remember(url) { mutableStateOf(false) }
    val dataSourceFactory = remember(appContext) { appContext.videoMediaDataSourceFactory() }
    var isMuted by rememberSaveable(url) { mutableStateOf(muted) }
    var isBuffering by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var controlsVisible by rememberSaveable(url) { mutableStateOf(!autoPlay) }
    var playbackError by remember(url) { mutableStateOf<String?>(null) }
    var isRateLimited by remember(url) { mutableStateOf(false) }
    var rateLimitCooldownSeconds by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedProgress by remember { mutableFloatStateOf(0f) }

    val mediaSourceFactory =
        remember(dataSourceFactory) {
            DefaultMediaSourceFactory(dataSourceFactory, DefaultExtractorsFactory())
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
        playbackError = null
        isRateLimited = false
        rateLimitCooldownSeconds = 0L
        positionMs = 0L
        durationMs = 0L
        bufferedProgress = 0f
        // Skip network hit if the CDN host is still in cooldown.
        val host = runCatching { url.toHttpUrl().host }.getOrNull()
        val blockedUntil = host?.let { RetryAfterTracker.blockedUntilMs(it) }
        if (blockedUntil != null) {
            isRateLimited = true
            rateLimitCooldownSeconds = ((blockedUntil - System.currentTimeMillis()) / 1_000L).coerceAtLeast(0L)
            playbackError = "Video rate limited by CDN"
        } else {
            exoPlayer.setMediaItem(MediaItem.fromUri(url))
            exoPlayer.prepare()
            exoPlayer.seekTo(0)
            exoPlayer.playWhenReady = active && autoPlay
            controlsVisible = !autoPlay
        }
    }

    // Tick down the rate-limit cooldown every second so the UI stays current.
    LaunchedEffect(isRateLimited) {
        if (!isRateLimited) return@LaunchedEffect
        while (rateLimitCooldownSeconds > 0L) {
            delay(1_000L)
            rateLimitCooldownSeconds = (rateLimitCooldownSeconds - 1L).coerceAtLeast(0L)
        }
        isRateLimited = false
        playbackError = null
    }

    // Pause as soon as this page is no longer active so audio never plays over the next video.
    LaunchedEffect(active, autoPlay) {
        exoPlayer.playWhenReady = active && autoPlay && playbackError == null
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    val fullscreen =
        rememberVideoFullscreenState(
            url = url,
            activity = activity,
            isPlaying = isPlaying,
            active = active,
            videoIsLandscape = videoIsLandscape,
            fullscreenByDefault = fullscreenByDefault,
            autoRotate = autoRotate,
            onFullscreenChange = onFullscreenChange,
        )

    LaunchedEffect(exoPlayer) {
        while (true) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            bufferedProgress = (exoPlayer.bufferedPercentage / PERCENT_DIVISOR).coerceIn(0f, 1f)
            delay(PROGRESS_UPDATE_MS)
        }
    }

    LaunchedEffect(isPlaying, controlsVisible) {
        if (isPlaying && controlsVisible) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    DisposableEffect(exoPlayer, url) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                }

                override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                    isPlaying = isPlayingNow
                    if (!isPlayingNow) controlsVisible = true
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    // Ignore audio (0x0); rotation applies only to wider-than-tall video.
                    videoSize.landscapeOrNull()?.let { videoIsLandscape = it }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "Video failed to load", error)
                    val rateLimited = error.hasHttpStatus(HTTP_TOO_MANY_REQUESTS)
                    isRateLimited = rateLimited
                    if (rateLimited) {
                        val host = runCatching { url.toHttpUrl().host }.getOrNull()
                        val blockedUntil = host?.let { RetryAfterTracker.blockedUntilMs(it) }
                        rateLimitCooldownSeconds =
                            if (blockedUntil != null) {
                                ((blockedUntil - System.currentTimeMillis()) / 1_000L).coerceAtLeast(0L)
                            } else {
                                DEFAULT_RATE_LIMIT_DISPLAY_SECONDS
                            }
                    }
                    playbackError = error.mediaLoadMessage()
                    isBuffering = false
                    controlsVisible = true
                }
            }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    val progress = remember(positionMs, durationMs) { positionMs.progressIn(durationMs) }

    Box(
        modifier =
            modifier.pointerInput(playbackError) {
                detectTapGestures {
                    if (playbackError == null) controlsVisible = !controlsVisible
                }
            },
    ) {
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
        LinearProgressIndicator(
            progress = { bufferedProgress.coerceAtLeast(progress) },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = Color.White.copy(alpha = PASSIVE_PROGRESS_ALPHA),
            trackColor = Color.White.copy(alpha = PASSIVE_PROGRESS_TRACK_ALPHA),
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
        )
        if (isBuffering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        if (playbackError != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = ERROR_OVERLAY_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text =
                            if (isRateLimited && rateLimitCooldownSeconds > 0L) {
                                "Video rate limited by CDN\nRetry in ${rateLimitCooldownSeconds}s"
                            } else {
                                playbackError.orEmpty()
                            },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { uriHandler.openUri(url) }) {
                        Text("Open in browser")
                    }
                }
            }
        } else if (controlsVisible) {
            VideoControls(
                isPlaying = isPlaying,
                isMuted = isMuted,
                isFullscreen = fullscreen.value,
                progress = progress,
                positionMs = positionMs,
                durationMs = durationMs,
                onPlayPause = {
                    exoPlayer.playWhenReady = !isPlaying
                    if (!isPlaying) controlsVisible = false
                },
                onMuteToggle = { isMuted = !isMuted },
                onFullscreenToggle = { fullscreen.value = !fullscreen.value },
                onSeek = { seekProgress ->
                    if (durationMs > 0) {
                        exoPlayer.seekTo((durationMs * seekProgress).toLong())
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Owns the video's fullscreen state and the side effects that drive it: auto-entering fullscreen
 * when playback starts (per the fullscreen/auto-rotate settings), exiting when the page is no
 * longer active, notifying the host, and applying/restoring the immersive + orientation
 * presentation. Returns the fullscreen [MutableState] so the caller can also toggle it manually.
 */
@Composable
private fun rememberVideoFullscreenState(
    url: String,
    activity: Activity?,
    isPlaying: Boolean,
    active: Boolean,
    videoIsLandscape: Boolean,
    fullscreenByDefault: Boolean,
    autoRotate: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
): MutableState<Boolean> {
    // The orientation the activity had before this player forced one, restored on exit/dispose.
    val originalOrientation =
        remember(activity) { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    val isFullscreen = rememberSaveable(url) { mutableStateOf(false) }
    // Tracks the one-time auto-entry so a manual exit is not immediately overridden while playing.
    var hasAutoFullscreened by remember(url) { mutableStateOf(false) }
    val shouldAutoFullscreen = fullscreenByDefault || (autoRotate && videoIsLandscape)

    LaunchedEffect(shouldAutoFullscreen, isPlaying) {
        if (shouldAutoFullscreen && isPlaying && !hasAutoFullscreened) {
            isFullscreen.value = true
            hasAutoFullscreened = true
        }
    }

    // Leaving this page (swipe/close) must never strand the activity locked or immersive.
    LaunchedEffect(active) {
        if (!active) {
            isFullscreen.value = false
            hasAutoFullscreened = false
        }
    }

    LaunchedEffect(isFullscreen.value) { onFullscreenChange(isFullscreen.value) }

    LaunchedEffect(activity, isFullscreen.value, autoRotate, videoIsLandscape) {
        activity?.applyVideoFullscreen(
            fullscreen = isFullscreen.value,
            lockLandscape = autoRotate && videoIsLandscape,
            originalOrientation = originalOrientation,
        )
    }

    // Always restore the original system bars and orientation when the player leaves composition.
    DisposableEffect(activity) {
        onDispose { activity?.applyVideoFullscreen(fullscreen = false, lockLandscape = false, originalOrientation) }
    }

    return isFullscreen
}

@Composable
private fun VideoControls(
    isPlaying: Boolean,
    isMuted: Boolean,
    isFullscreen: Boolean,
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onMuteToggle: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timestampText =
        "${positionMs.formatTimestamp()} / " +
            durationMs.formatTimestamp()

    Box(
        modifier = modifier.background(Color.Black.copy(alpha = CONTROLS_OVERLAY_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        FilledTonalIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(72.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(40.dp),
            )
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Slider(
                value = progress,
                onValueChange = onSeek,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = timestampText,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMuteToggle,
                        modifier = Modifier.widthIn(min = 48.dp),
                    ) {
                        Icon(
                            imageVector =
                                if (isMuted) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White,
                        )
                    }
                    IconButton(
                        onClick = onFullscreenToggle,
                        modifier = Modifier.widthIn(min = 48.dp),
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface VideoPlayerEntryPoint {
    @VideoMediaDataSource
    fun videoDataSourceFactory(): DataSource.Factory
}

private fun Context.videoMediaDataSourceFactory(): DataSource.Factory =
    EntryPointAccessors.fromApplication(this, VideoPlayerEntryPoint::class.java).videoDataSourceFactory()

/** True/false when this is real video (non-zero size), null for audio-only (0x0) tracks. */
private fun VideoSize.landscapeOrNull(): Boolean? = if (width > 0 && height > 0) width > height else null

/** Unwraps the hosting [Activity] from a (possibly wrapped) composition [Context], if any. */
private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * Toggles immersive full-screen presentation on the activity: hides/shows the system bars and,
 * when [lockLandscape] is set, forces sensor-landscape orientation. Exiting restores the system
 * bars and [originalOrientation], so the activity is never left locked or immersive.
 */
private fun Activity.applyVideoFullscreen(
    fullscreen: Boolean,
    lockLandscape: Boolean,
    originalOrientation: Int,
) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (fullscreen) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        requestedOrientation =
            if (lockLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                originalOrientation
            }
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
        requestedOrientation = originalOrientation
    }
}

private fun Long.progressIn(durationMs: Long): Float =
    if (durationMs > 0L) {
        (toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

private fun Long.formatTimestamp(): String {
    val totalSeconds = (this / MILLIS_PER_SECOND).coerceAtLeast(0L)
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun PlaybackException.mediaLoadMessage(): String =
    if (hasHttpStatus(HTTP_TOO_MANY_REQUESTS)) {
        "Video rate limited. Try again later."
    } else {
        "Video unavailable"
    }

private fun Throwable.hasHttpStatus(statusCode: Int): Boolean =
    generateSequence(this as Throwable?) { it.cause }
        .any { throwable -> throwable.message?.contains(statusCode.toString()) == true }

private const val TAG = "OrbinVideoPlayer"
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val DEFAULT_RATE_LIMIT_DISPLAY_SECONDS = 300L
private const val ERROR_OVERLAY_ALPHA = 0.68f
private const val CONTROLS_OVERLAY_ALPHA = 0.38f
private const val PASSIVE_PROGRESS_ALPHA = 0.65f
private const val PASSIVE_PROGRESS_TRACK_ALPHA = 0.22f
private const val PERCENT_DIVISOR = 100f
private const val PROGRESS_UPDATE_MS = 250L
private const val CONTROLS_AUTO_HIDE_MS = 2_500L
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
