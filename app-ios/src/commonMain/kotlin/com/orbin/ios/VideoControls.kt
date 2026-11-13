package com.orbin.ios

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_media_forward_video
import com.orbin.ios.resources.ios_media_loading_video
import com.orbin.ios.resources.ios_media_next_video
import com.orbin.ios.resources.ios_media_pause_video
import com.orbin.ios.resources.ios_media_play_video
import com.orbin.ios.resources.ios_media_restart_video
import com.orbin.ios.resources.ios_media_rewind_video
import com.orbin.uinext.ReaderMediaControls
import com.orbin.uinext.reader
import org.jetbrains.compose.resources.stringResource

/** What the video on screen is doing, for [VideoControls]. Times are in seconds; 0 while unknown. */
internal data class VideoState(
    val playing: Boolean = false,
    val muted: Boolean = true,
    val position: Double = 0.0,
    val duration: Double = 0.0,
    val loading: Boolean = false,
    val displayReady: Boolean = false,
)

/**
 * The viewer's own controls over a playing video, drawn by the app rather than the system player
 * so the page stays in the app and a vertical swipe still moves to the next file: tap to pause or
 * play, double-tap either half to skip back or ahead ten seconds, and a bar to scrub.
 */
@Composable
internal fun VideoControls(
    state: VideoState,
    onTogglePlay: () -> Unit,
    onSeekTo: (Double) -> Unit,
    modifier: Modifier = Modifier,
    onDoubleTapSave: (() -> Unit)? = null,
) {
    val latest by rememberUpdatedState(state)
    val togglePlay by rememberUpdatedState(onTogglePlay)
    val seekTo by rememberUpdatedState(onSeekTo)
    val save by rememberUpdatedState(onDoubleTapSave)
    Box(
        modifier.pointerInput(Unit) {
            // Taps only: drags are left unconsumed so the pager still turns the page.
            detectTapGestures(
                onTap = { togglePlay() },
                onDoubleTap = { at ->
                    if (save != null) {
                        save?.invoke()
                    } else {
                        val skip = if (at.x >= size.width / 2f) SKIP_SECONDS else -SKIP_SECONDS
                        seekTo(clampedSeek(latest.position + skip, latest.duration))
                    }
                },
            )
        },
    ) {
        if (state.loading) {
            val loadingLabel = stringResource(Res.string.ios_media_loading_video)
            LinearProgressIndicator(
                color = reader.accent,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .testTag("ios-video-loading")
                        .semantics { contentDescription = loadingLabel },
            )
        }
        ReaderMediaControls(
            playing = state.playing,
            loading = state.loading,
            positionSeconds = state.position,
            durationSeconds = state.duration,
            onPlayPause = onTogglePlay,
            onSeek = onSeekTo,
            playLabel = stringResource(Res.string.ios_media_play_video),
            pauseLabel = stringResource(Res.string.ios_media_pause_video),
            restartLabel = stringResource(Res.string.ios_media_restart_video),
            rewindLabel = stringResource(Res.string.ios_media_rewind_video),
            fastForwardLabel = stringResource(Res.string.ios_media_forward_video),
            nextLabel = stringResource(Res.string.ios_media_next_video),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** [seconds] kept within the video; with no known length, only kept from going below zero. */
internal fun clampedSeek(
    seconds: Double,
    duration: Double,
): Double = if (duration > 0.0) seconds.coerceIn(0.0, duration) else seconds.coerceAtLeast(0.0)

/** A time as m:ss, the way the system player shows it. */
internal fun clock(seconds: Double): String {
    val whole = seconds.toInt().coerceAtLeast(0)
    return "${whole / SECONDS_PER_MINUTE}:${(whole % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}

/** WebM state includes buffering/display readiness; older four-field snapshots remain readable. */
internal fun parseWebMState(text: String): VideoState? {
    val fields =
        text.split(',').takeIf { it.size == WEBM_STATE_FIELDS || it.size == WEBM_LEGACY_STATE_FIELDS }
            ?: return null
    val (playing, positionText, durationText) = fields
    val muted = fields[WEBM_MUTED_FIELD]
    val validFlags =
        playing in WEBM_FLAGS && muted in WEBM_FLAGS && fields.drop(WEBM_LEGACY_STATE_FIELDS).all { it in WEBM_FLAGS }
    if (!validFlags) return null
    val position = positionText.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    val duration = durationText.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
    return VideoState(
        playing = playing == "1",
        muted = muted == "1",
        position = position,
        duration = duration,
        loading = fields.getOrNull(WEBM_LOADING_FIELD) == "1",
        displayReady = fields.getOrNull(WEBM_READY_FIELD) == "1",
    )
}

/** Last viewer audio choice for this process; inline previews never use or change it. */
internal object SessionAudio {
    var muted: Boolean = true

    fun toggle(current: Boolean): Boolean = (!current).also { muted = it }
}

/** Apply the latest choice even if a toggle happened before the WebKit page finished loading. */
internal fun webMStateScript(muted: Boolean): String =
    "(function(){var v=document.getElementById('v');if(!v)return null;v.muted=$muted;" +
        "return (v.paused?'0':'1')+','+v.currentTime+','+v.duration+','+(v.muted?'1':'0')})()"

private const val WEBM_STATE_FIELDS = 6
private const val WEBM_LEGACY_STATE_FIELDS = 4
private const val WEBM_MUTED_FIELD = 3
private const val WEBM_LOADING_FIELD = 4
private const val WEBM_READY_FIELD = 5
private val WEBM_FLAGS = setOf("0", "1")
private const val SKIP_SECONDS = 10.0
private const val SECONDS_PER_MINUTE = 60
