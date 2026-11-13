package com.orbin.media.video

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import com.orbin.uinext.ReaderMediaControls
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType

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
    loading: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedProgress: Float,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        ReaderMediaControls(
            playing = isPlaying,
            loading = loading,
            positionSeconds = positionMs / 1_000.0,
            durationSeconds = durationMs / 1_000.0,
            onPlayPause = onPlayPause,
            onSeek = { seconds ->
                if (durationMs > 0) onSeek((seconds * 1_000.0 / durationMs).toFloat().coerceIn(0f, 1f))
            },
            bufferedFraction = bufferedProgress,
            onScrubbingChange = onScrubbingChange,
            modifier = Modifier.align(Alignment.BottomCenter),
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
        style = OrbinType.body,
        modifier =
            modifier
                .clip(RoundedCornerShape(OrbinRadius.pill))
                .background(Color.Black.copy(alpha = SKIP_LABEL_FILL_ALPHA))
                .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
