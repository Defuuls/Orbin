package com.orbin.media.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.util.Rational
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.PlayerView
import co.touchlab.kermit.Logger
import com.orbin.core.common.link.SafeExternalLinks
import com.orbin.media.R
import com.orbin.media.hasHttpStatus
import com.orbin.network.interceptor.RetryAfter
import com.orbin.network.interceptor.RetryAfterTracker
import com.orbin.uinext.FeedPlaybackControls
import com.orbin.uinext.InlineAction
import com.orbin.uinext.NextCircularProgress
import com.orbin.uinext.tokens.NextType
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
    onControlsToggle: ((Boolean) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    loop: Boolean? = null,
    /** Compact tap-to-pause controls for a full-page feed, without viewer chrome or auto-PiP. */
    feedMode: Boolean = false,
    onFeedLike: (() -> Unit)? = null,
    onPlayIntentChange: ((Boolean) -> Unit)? = null,
    onMuteIntentChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val playbackSession =
        remember(url, autoPlay) {
            PlaybackSession(autoPlay, lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
    val currentSession by rememberUpdatedState(playbackSession)
    var playRequested by remember(playbackSession) { mutableStateOf(playbackSession.requestedPlaying) }
    val dataSourceFactory = remember(appContext) { appContext.videoMediaDataSourceFactory() }
    var isMuted by rememberSaveable(url, muted) { mutableStateOf(muted) }
    var isBuffering by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var controlsVisible by rememberSaveable(url) { mutableStateOf(!autoPlay) }
    var playbackError by remember(url) { mutableStateOf<String?>(null) }
    var isRateLimited by remember(url) { mutableStateOf(false) }
    var rateLimitCooldownSeconds by remember(url) { mutableLongStateOf(0L) }
    // Whether the cooldown ends in an automatic reload; false once the retry policy gives up.
    var autoRetryPending by remember(url) { mutableStateOf(false) }
    var autoRetries by remember(url) { mutableIntStateOf(0) }
    val retryPolicy = remember { RateLimitRetryPolicy() }
    // Bumped when a rate-limit cooldown ends so the player loads the video again by itself.
    var loadAttempt by remember(url) { mutableIntStateOf(0) }
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
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    // handleAudioFocus =
                    !muted,
                ).build()
                .apply {
                    repeatMode = repeatModeFor(loop ?: shouldLoop(0L))
                    volume = if (muted) 0f else 1f
                    playWhenReady = playbackSession.shouldPlay(active, hasError = false)
                }
        }

    val currentActive by rememberUpdatedState(active)
    val currentPlaybackError by rememberUpdatedState(playbackError)
    DisposableEffect(lifecycleOwner, exoPlayer, feedMode) {
        if (feedMode) activity?.disableAutomaticVideoPip()
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                        if (feedMode || activity?.isInPictureInPictureMode != true) {
                            currentSession.setForeground(false)
                            exoPlayer.pause()
                        }
                    }
                    Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                        currentSession.setForeground(true)
                        exoPlayer.playWhenReady =
                            currentSession.shouldPlay(
                                currentActive,
                                hasError = currentPlaybackError != null,
                            )
                    }
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(exoPlayer, isPlaying, active, feedMode) {
        if (activity != null) {
            // A queued composition must not re-enable PiP after the native pause callback.
            val enabled = !feedMode && currentActive && exoPlayer.isPlaying
            val width = exoPlayer.videoSize.width
            val height = exoPlayer.videoSize.height
            val builder = PictureInPictureParams.Builder().setAutoEnterEnabled(enabled)
            if (width > 0 && height > 0) {
                val ratio = width.toFloat() / height.toFloat()
                if (ratio in 0.42f..2.38f) {
                    builder.setAspectRatio(Rational(width, height))
                }
            }
            runCatching { activity.setPictureInPictureParams(builder.build()) }
        }
    }

    LaunchedEffect(url, loadAttempt) {
        playbackError = null
        isRateLimited = false
        rateLimitCooldownSeconds = 0L
        positionMs = 0L
        durationMs = 0L
        bufferedProgress = 0f
        // The data source checks encrypted media first, then applies CDN backoff to cache misses.
        // A UI-level cooldown gate would prevent already-cached clips from playing.
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.seekTo(PlaybackPositions.resumeAt(url))
        exoPlayer.playWhenReady = playbackSession.shouldPlay(active, hasError = false)
        controlsVisible = !playbackSession.requestedPlaying
    }

    // Tick down the rate-limit cooldown every second so the UI stays current.
    LaunchedEffect(isRateLimited) {
        if (!isRateLimited) return@LaunchedEffect
        while (rateLimitCooldownSeconds > 0L) {
            delay(1_000L)
            rateLimitCooldownSeconds = (rateLimitCooldownSeconds - 1L).coerceAtLeast(0L)
        }
        // Out of automatic retries: leave the error up so the reader decides when to try again.
        if (!autoRetryPending) return@LaunchedEffect
        isRateLimited = false
        playbackError = null
        autoRetries++
        loadAttempt++
    }

    // Pause as soon as this page is no longer active so audio never plays over the next video.
    LaunchedEffect(active, autoPlay, playbackError) {
        exoPlayer.playWhenReady = playbackSession.shouldPlay(active, hasError = playbackError != null)
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
        exoPlayer.setAudioAttributes(exoPlayer.audioAttributes, !isMuted)
    }

    // Explicit playback preferences override the legacy duration-based default.
    LaunchedEffect(durationMs, loop) {
        exoPlayer.repeatMode = repeatModeFor(loop ?: shouldLoop(durationMs))
    }

    // Turning the phone sideways is the fullscreen button.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (active && !feedMode) {
        VideoFullscreenEffect(
            activity = activity,
            fullscreen = landscape,
            onFullscreenChange = onFullscreenChange,
        )
    }

    LaunchedEffect(exoPlayer, active, isPlaying) {
        positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        durationMs = exoPlayer.duration.coerceAtLeast(0L)
        bufferedProgress = (exoPlayer.bufferedPercentage / PERCENT_DIVISOR).coerceIn(0f, 1f)
        while (active && isPlaying) {
            delay(PROGRESS_UPDATE_MS)
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            bufferedProgress = (exoPlayer.bufferedPercentage / PERCENT_DIVISOR).coerceIn(0f, 1f)
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
                    if (isPlayingNow) autoRetries = 0
                    if (!isPlayingNow) {
                        // Auto-enter is read by Android before a later Compose frame may run.
                        activity?.disableAutomaticVideoPip()
                        controlsVisible = true
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Logger.w(tag = TAG) { "Video failed to load (${error.errorCodeName})" }
                    val rateLimited = error.hasHttpStatus(RetryAfter.HTTP_TOO_MANY_REQUESTS)
                    isRateLimited = rateLimited
                    if (rateLimited) {
                        val address = runCatching { url.toHttpUrl() }.getOrNull()
                        val blockedUntil = address?.let { RetryAfterTracker.blockedUntilMs(it.host, it.toString()) }
                        val retryAfterSeconds =
                            if (blockedUntil != null) {
                                ((blockedUntil - System.currentTimeMillis()) / 1_000L).coerceAtLeast(0L)
                            } else {
                                RetryAfter.DEFAULT_SECONDS
                            }
                        val retryDelayMs = retryPolicy.delayMs(autoRetries, retryAfterSeconds)
                        autoRetryPending = retryDelayMs != null
                        rateLimitCooldownSeconds =
                            retryDelayMs?.let { (it + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND } ?: 0L
                    }
                    playbackError = error.mediaLoadMessage()
                    isBuffering = false
                    controlsVisible = true
                }
            }
        exoPlayer.addListener(listener)
        onDispose {
            activity?.disableAutomaticVideoPip()
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

    val haptics = LocalHapticFeedback.current
    val skip: (Boolean) -> Unit = { forward ->
        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
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

    val togglePlayback = {
        val ended = exoPlayer.playbackState == Player.STATE_ENDED
        if (ended) exoPlayer.seekTo(0L)
        playbackSession.toggle(ended)
        playRequested = playbackSession.requestedPlaying
        onPlayIntentChange?.invoke(playbackSession.requestedPlaying)
        if (!playbackSession.requestedPlaying) activity?.disableAutomaticVideoPip()
        exoPlayer.playWhenReady = playbackSession.shouldPlay(active, hasError = playbackError != null)
        if (playbackSession.requestedPlaying) {
            controlsVisible = false
            onControlsToggle?.invoke(false)
        }
    }
    val toggleMute: () -> Unit = {
        isMuted = !isMuted
        SessionAudio.muted = isMuted
        onMuteIntentChange?.invoke(isMuted)
    }
    val viewerGestures =
        if (feedMode) {
            Modifier
        } else {
            Modifier
                .pointerInput(playbackError, exoPlayer, onLongPress, onControlsToggle) {
                    detectTapGestures(
                        onLongPress = onLongPress?.let { handler -> { handler() } },
                        // Toggle on release rather than waiting out the double-tap timeout: a double
                        // tap toggles twice, which leaves the controls as they were, and then seeks.
                        onPress = {
                            if (tryAwaitRelease() && playbackError == null) {
                                val newVisible = !controlsVisible
                                controlsVisible = newVisible
                                onControlsToggle?.invoke(newVisible)
                            }
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
                }
        }
    Box(modifier = modifier.then(viewerGestures)) {
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
                    if (isRateLimited && !autoRetryPending) {
                        InlineAction(
                            label = stringResource(R.string.media_retry),
                            accent = true,
                            onClick = {
                                RetryAfterTracker.clear()
                                autoRetries = 0
                                loadAttempt++
                            },
                        )
                    }
                    InlineAction(
                        label = stringResource(R.string.media_open_in_browser),
                        accent = true,
                        onClick = { SafeExternalLinks.open(context, url) },
                    )
                }
            }
        } else if (feedMode) {
            FeedPlaybackControls(
                playing = isPlaying || (isBuffering && playbackSession.requestedPlaying),
                muted = isMuted,
                progress = progress,
                onPlayPause = togglePlayback,
                onMuteToggle = toggleMute,
                onDoubleTap = onFeedLike,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (controlsVisible) {
            VideoControls(
                isPlaying = isPlaying,
                loading = isBuffering && playRequested,
                positionMs = positionMs,
                durationMs = durationMs,
                bufferedProgress = bufferedProgress,
                onPlayPause = togglePlayback,
                onSeek = { seekProgress ->
                    if (durationMs > 0) {
                        val target = (durationMs * seekProgress).toLong()
                        exoPlayer.seekTo(target)
                        positionMs = target
                    }
                },
                onScrubbingChange = { scrubbing = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Update Android synchronously: backgrounding can happen before Compose observes a pause. */
private fun Activity.disableAutomaticVideoPip() {
    runCatching {
        setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
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

private const val TAG = "OrbinVideoPlayer"
private const val ERROR_OVERLAY_ALPHA = 0.68f
private const val PERCENT_DIVISOR = 100f
private const val PROGRESS_UPDATE_MS = 250L
private const val CONTROLS_AUTO_HIDE_MS = 2_500L
internal const val MILLIS_PER_SECOND = 1_000L
internal const val SECONDS_PER_MINUTE = 60L
internal const val SKIP_SECONDS = 5
internal const val LOOP_UNDER_MS = 30_000L
private const val SKIP_LABEL_VISIBLE_MS = 700L
internal const val SKIP_LABEL_FILL_ALPHA = 0.55f
