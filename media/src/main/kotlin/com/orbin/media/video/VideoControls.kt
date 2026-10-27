package com.orbin.media.video

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orbin.uinext.NextIconAction
import com.orbin.uinext.NextSlider
import com.orbin.uinext.next
import com.orbin.uinext.nextClickable
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextType

// The viewer's overlay controls, scrub bubble, skip labels and passive progress line, drawn over
// [VideoPlayer].

@Composable
internal fun VideoFullscreenEffect(
    activity: Activity?,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
) {
    val currentOnFullscreenChange by rememberUpdatedState(onFullscreenChange)
    LaunchedEffect(fullscreen) { currentOnFullscreenChange(fullscreen) }
    LaunchedEffect(activity, fullscreen) { activity?.applyVideoFullscreen(fullscreen) }
    DisposableEffect(activity) {
        onDispose {
            currentOnFullscreenChange(false)
            activity?.applyVideoFullscreen(fullscreen = false)
        }
    }
}

@Composable
internal fun VideoControls(
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
internal fun SkipLabel(
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
internal fun VideoPassiveProgress(
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
