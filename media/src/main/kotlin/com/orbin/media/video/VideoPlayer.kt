package com.orbin.media.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.PlayerView
import com.orbin.core.common.link.SafeExternalLinks
import com.orbin.media.R
import com.orbin.media.di.VideoMediaDataSource
import com.orbin.network.interceptor.RetryAfterTracker
import com.orbin.uinext.InlineAction
import com.orbin.uinext.NextCircularProgress
import com.orbin.uinext.NextIconAction
import com.orbin.uinext.NextSlider
import com.orbin.uinext.next
import com.orbin.uinext.nextClickable
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * A Media3/ExoPlayer-backed video player. The player is created per [url], loops by default, and
 * exposes a Loop/Once control so the behavior can be changed while it is playing. The player is
 * released when the composable leaves composition so there are no leaked players. Autoplay and
 * the initial mute state come from the caller (by default [SessionAudio]); tapping the video reveals compact
 * controls without permanently covering playing media, and double tapping its left or right half
 * skips back or forward [SKIP_SECONDS] seconds.
 */
@Suppress("UnsafeOptInUsageError")
@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    muted: Boolean = SessionAudio.muted,
    active: Boolean = true,
    onFullscreenChange: (Boolean) -> Unit = {},
    onLongPress: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = remember(context) { context.findActivity() }
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
    var scrubbing by remember { mutableStateOf(false) }
    // Seconds skipped by the current run of double taps; negative is backwards, 0 hides the label.
    var skipSeconds by remember(url) { mutableIntStateOf(0) }
    var skipGeneration by remember(url) { mutableIntStateOf(0) }

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
                    repeatMode = repeatModeFor(shouldLoop(0L))
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
            // Pick up where this video was left, for as long as Orbin keeps running.
            exoPlayer.seekTo(PlaybackPositions.resumeAt(url))
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

    // Short clips loop like GIFs; anything longer plays once. Decided by length, not a setting.
    LaunchedEffect(durationMs) {
        exoPlayer.repeatMode = repeatModeFor(shouldLoop(durationMs))
    }

    // Turning the phone sideways is the fullscreen button.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    VideoFullscreenEffect(
        activity = activity,
        fullscreen = active && landscape,
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

    LaunchedEffect(isPlaying, controlsVisible, scrubbing) {
        if (isPlaying && controlsVisible && !scrubbing) {
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
            PlaybackPositions.remember(url, exoPlayer.currentPosition, exoPlayer.duration)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    val progress = remember(positionMs, durationMs) { positionMs.progressIn(durationMs) }

    // Hide the skip label once the taps stop; a new double tap restarts the wait.
    LaunchedEffect(skipGeneration) {
        if (skipSeconds == 0) return@LaunchedEffect
        delay(SKIP_LABEL_VISIBLE_MS)
        skipSeconds = 0
    }

    val skip: (Boolean) -> Unit = { forward ->
        exoPlayer.seekTo(
            seekTargetMs(
                currentMs = exoPlayer.currentPosition,
                durationMs = exoPlayer.duration,
                forward = forward,
            ),
        )
        positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        // Keep counting while the taps go the same way, so three quick double taps read "+15s".
        val step = if (forward) SKIP_SECONDS else -SKIP_SECONDS
        skipSeconds = if (skipSeconds != 0 && (skipSeconds > 0) == forward) skipSeconds + step else step
        skipGeneration++
    }
    val skipBackLabel = stringResource(R.string.media_skip_back)
    val skipForwardLabel = stringResource(R.string.media_skip_forward)

    Box(
        modifier =
            modifier
                .pointerInput(playbackError, exoPlayer, onLongPress) {
                    detectTapGestures(
                        onLongPress = onLongPress?.let { handler -> { handler() } },
                        // Toggle on release rather than waiting out the double-tap timeout: a double
                        // tap toggles twice, which leaves the controls as they were, and then seeks.
                        onPress = {
                            if (tryAwaitRelease() && playbackError == null) controlsVisible = !controlsVisible
                        },
                        // Left half goes back, right half goes forward, as in most video players.
                        onDoubleTap = { offset ->
                            if (playbackError == null) skip(offset.x >= size.width / 2f)
                        },
                    )
                }.semantics {
                    if (playbackError == null) {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(skipBackLabel) {
                                    skip(false)
                                    true
                                },
                                CustomAccessibilityAction(skipForwardLabel) {
                                    skip(true)
                                    true
                                },
                            )
                    }
                },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    // Let the Compose pointerInput above receive taps (controls toggle).
                    isClickable = false
                    isFocusable = false
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.useController = false
            },
        )
        VideoPassiveProgress(
            progress = progress,
            bufferedProgress = bufferedProgress,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        )
        if (isBuffering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NextCircularProgress()
            }
        }
        if (skipSeconds != 0) {
            SkipLabel(
                seconds = skipSeconds,
                modifier =
                    Modifier
                        .align(if (skipSeconds > 0) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 32.dp),
            )
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
                        style = NextType.body,
                        textAlign = TextAlign.Center,
                    )
                    InlineAction(
                        label = stringResource(R.string.media_open_in_browser),
                        accent = true,
                        onClick = { SafeExternalLinks.open(context, url) },
                    )
                }
            }
        } else if (controlsVisible) {
            VideoControls(
                isPlaying = isPlaying,
                isMuted = isMuted,
                progress = progress,
                positionMs = positionMs,
                durationMs = durationMs,
                onPlayPause = {
                    exoPlayer.playWhenReady = !isPlaying
                    if (!isPlaying) controlsVisible = false
                },
                onMuteToggle = {
                    isMuted = !isMuted
                    SessionAudio.muted = isMuted
                },
                onSeek = { seekProgress ->
                    if (durationMs > 0) {
                        exoPlayer.seekTo((durationMs * seekProgress).toLong())
                    }
                },
                scrubbing = scrubbing,
                onScrubbingChange = { scrubbing = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Where each longer video was left, so reopening it resumes rather than restarts.
 *
 * Only videos too long to loop are remembered, and only until they are nearly finished; a
 * bounded, in-memory map that ends with the process, so nothing about viewing is stored.
 */
internal object PlaybackPositions {
    private const val MAX_REMEMBERED = 50
    private const val NEAR_END_MS = 3_000L
    private val positions =
        object : LinkedHashMap<String, Long>(MAX_REMEMBERED, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > MAX_REMEMBERED
        }

    @Synchronized
    fun resumeAt(url: String): Long = positions[url] ?: 0L

    @Synchronized
    fun remember(
        url: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        val worthResuming = !shouldLoop(durationMs) && positionMs in 1 until durationMs - NEAR_END_MS
        if (worthResuming) positions[url] = positionMs else positions.remove(url)
    }

    @Synchronized
    internal fun clear() = positions.clear()
}

/**
 * Whether the next video opens muted.
 *
 * Every video starts muted until the reader unmutes one; from then on, for as long as Orbin is
 * running, the next video opens with sound. No setting: the last choice is the answer.
 */
object SessionAudio {
    @Volatile
    var muted: Boolean = true
}

/**
 * Hides the system bars while [fullscreen] is true and tells the host, so the gallery can put its
 * own chrome away too. Restores the bars when fullscreen ends or the player leaves composition.
 */
@Composable
private fun VideoFullscreenEffect(
    activity: Activity?,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
) {
    LaunchedEffect(fullscreen) { onFullscreenChange(fullscreen) }
    LaunchedEffect(activity, fullscreen) { activity?.applyVideoFullscreen(fullscreen) }
    DisposableEffect(activity) {
        onDispose { activity?.applyVideoFullscreen(fullscreen = false) }
    }
}

@Composable
private fun VideoControls(
    isPlaying: Boolean,
    isMuted: Boolean,
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onMuteToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    scrubbing: Boolean,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timestampText =
        "${positionMs.formatTimestamp()} / " +
            durationMs.formatTimestamp()

    Box(
        modifier = modifier.background(Color.Black.copy(alpha = CONTROLS_OVERLAY_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(next.raised.copy(alpha = PLAY_BUTTON_FILL_ALPHA))
                    .nextClickable(
                        onClickLabel = if (isPlaying) "Pause" else "Play",
                        onClick = onPlayPause,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = next.ink,
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
            if (scrubbing) {
                // Where the drag points, above the bar, so a seek can be aimed rather than guessed.
                ScrubBubble(
                    text = positionMs.formatTimestamp(),
                    fraction = progress,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            NextSlider(
                value = progress,
                onValueChange = onSeek,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
                onDragStateChange = onScrubbingChange,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = timestampText,
                    color = Color.White,
                    style = NextType.footnote,
                )
                NextIconAction(
                    imageVector =
                        if (isMuted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    onClick = onMuteToggle,
                    tint = Color.White,
                )
            }
        }
    }
}

/** The time under the finger while scrubbing, riding above the thumb. */
@Composable
private fun ScrubBubble(
    text: String,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val bubbleWidth = SCRUB_BUBBLE_WIDTH
        val x = ((maxWidth - bubbleWidth) * fraction.coerceIn(0f, 1f))
        Text(
            text = text,
            color = Color.White,
            style = NextType.footnote,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .padding(start = x)
                    .width(bubbleWidth)
                    .clip(RoundedCornerShape(NextRadius.pill))
                    .background(Color.Black.copy(alpha = SKIP_LABEL_FILL_ALPHA))
                    .padding(vertical = 4.dp),
        )
    }
}

/** "+5s" / "−5s" on the side that was double tapped, over whatever the video is showing. */
@Composable
private fun SkipLabel(
    seconds: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (seconds > 0) "+${seconds}s" else "\u2212${-seconds}s",
        color = Color.White,
        style = NextType.body,
        modifier =
            modifier
                .clip(RoundedCornerShape(NextRadius.pill))
                .background(Color.Black.copy(alpha = SKIP_LABEL_FILL_ALPHA))
                .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Always-on bottom scrub track — Next hairline language (3dp pill) with a white buffered fill and
 * accent played fill so it stays readable over video without Material LinearProgressIndicator.
 */
@Composable
private fun VideoPassiveProgress(
    progress: Float,
    bufferedProgress: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(NextRadius.pill)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PASSIVE_PROGRESS_HEIGHT)
                .clip(shape)
                .background(Color.White.copy(alpha = PASSIVE_PROGRESS_TRACK_ALPHA)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(bufferedProgress.coerceAtLeast(progress).coerceIn(0f, 1f))
                    .background(Color.White.copy(alpha = PASSIVE_PROGRESS_ALPHA)),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(next.accent),
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface VideoPlayerEntryPoint {
    @VideoMediaDataSource
    fun videoDataSourceFactory(): DataSource.Factory
}

internal fun Context.videoMediaDataSourceFactory(): DataSource.Factory =
    EntryPointAccessors.fromApplication(this, VideoPlayerEntryPoint::class.java).videoDataSourceFactory()

/** Unwraps the hosting [Activity] from a (possibly wrapped) composition [Context], if any. */
private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/** Hides the system bars for fullscreen playback, or brings them back. */
private fun Activity.applyVideoFullscreen(fullscreen: Boolean) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (fullscreen) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

/**
 * Where a double tap lands: [SKIP_SECONDS] from [currentMs], never before the start and, once the
 * duration is known, never past the end. An unknown duration (still loading, or a live stream)
 * only clamps at zero.
 */
internal fun seekTargetMs(
    currentMs: Long,
    durationMs: Long,
    forward: Boolean,
): Long {
    val step = SKIP_SECONDS * MILLIS_PER_SECOND
    val target = currentMs.coerceAtLeast(0L) + if (forward) step else -step
    return if (durationMs > 0L) target.coerceIn(0L, durationMs) else target.coerceAtLeast(0L)
}

/** Clips up to [LOOP_UNDER_MS] loop like GIFs; longer videos play once. Unknown length loops. */
internal fun shouldLoop(durationMs: Long): Boolean = durationMs <= LOOP_UNDER_MS

internal fun repeatModeFor(loopEnabled: Boolean): Int =
    if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

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
private const val PLAY_BUTTON_FILL_ALPHA = 0.92f
private val PASSIVE_PROGRESS_HEIGHT = 3.dp
private val SCRUB_BUBBLE_WIDTH = 64.dp
private const val PERCENT_DIVISOR = 100f
private const val PROGRESS_UPDATE_MS = 250L
private const val CONTROLS_AUTO_HIDE_MS = 2_500L
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
internal const val SKIP_SECONDS = 5
private const val LOOP_UNDER_MS = 30_000L
private const val SKIP_LABEL_VISIBLE_MS = 700L
private const val SKIP_LABEL_FILL_ALPHA = 0.55f
