package com.orbin.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_media_play_video
import org.jetbrains.compose.resources.stringResource

/** What the video on screen is doing, for [VideoControls]. Times are in seconds; 0 while unknown. */
internal data class VideoState(
    val playing: Boolean = false,
    val position: Double = 0.0,
    val duration: Double = 0.0,
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
) {
    val latest by rememberUpdatedState(state)
    Box(
        modifier.pointerInput(Unit) {
            // Taps only: drags are left unconsumed so the pager still turns the page.
            detectTapGestures(
                onTap = { onTogglePlay() },
                onDoubleTap = { at ->
                    val skip = if (at.x >= size.width / 2f) SKIP_SECONDS else -SKIP_SECONDS
                    onSeekTo(clampedSeek(latest.position + skip, latest.duration))
                },
            )
        },
    ) {
        if (!state.playing) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(Res.string.ios_media_play_video),
                tint = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(PLAY_SIZE)
                        .background(Color.Black.copy(alpha = SCRIM), CircleShape)
                        .padding(12.dp),
            )
        }
        if (state.duration > 0.0) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(clock(state.position), color = Color.White)
                Slider(
                    value = (state.position / state.duration).toFloat().coerceIn(0f, 1f),
                    onValueChange = { fraction -> onSeekTo(fraction * state.duration) },
                    colors =
                        SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = SCRIM),
                        ),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(clock(state.duration), color = Color.White)
            }
        }
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

/** "playing,position,duration" as the WebM page's state script reports it, or null if the page is not ready. */
internal fun parseWebMState(text: String): VideoState? {
    val parts = text.split(',')
    if (parts.size != 3) return null
    val position = parts[1].toDoubleOrNull() ?: return null
    val duration = parts[2].toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
    return VideoState(playing = parts[0] == "1", position = position, duration = duration)
}

private const val SKIP_SECONDS = 10.0
private const val SECONDS_PER_MINUTE = 60
private const val SCRIM = 0.4f
private val PLAY_SIZE = 64.dp
