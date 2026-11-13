package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_mute_video
import com.orbin.uinext.resources.reader_pause_video
import com.orbin.uinext.resources.reader_play_video
import com.orbin.uinext.resources.reader_unmute_video
import org.jetbrains.compose.resources.stringResource

/** The same media controls on both hosts; drags remain available to the vertical feed pager. */
@Composable
fun FeedPlaybackControls(
    playing: Boolean,
    muted: Boolean,
    onPlayPause: () -> Unit,
    onMuteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    onDoubleTap: (() -> Unit)? = null,
) {
    val toggle by rememberUpdatedState(onPlayPause)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val playbackLabel = stringResource(if (playing) Res.string.reader_pause_video else Res.string.reader_play_video)
    val progressValue = progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    Box(
        modifier
            .testTag(FeedPlaybackTags.SURFACE)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            ).semantics {
                contentDescription = playbackLabel
                role = Role.Button
                onClick(label = playbackLabel) {
                    toggle()
                    true
                }
            }.pointerInput(onDoubleTap != null) {
                detectTapGestures(
                    // A single tap pauses on release, before the double-tap timeout or a background event.
                    // A recognized double tap toggles twice and restores its original play intent.
                    onPress = { if (tryAwaitRelease()) toggle() },
                    onDoubleTap = if (doubleTap == null) null else { _ -> doubleTap?.invoke() },
                )
            },
    ) {
        if (!playing) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.Center).size(72.dp),
            )
        }
        IconButton(
            onClick = onMuteToggle,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(8.dp)
                    .size(48.dp)
                    .testTag(FeedPlaybackTags.MUTE),
        ) {
            Icon(
                imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription =
                    stringResource(
                        if (muted) Res.string.reader_unmute_video else Res.string.reader_mute_video,
                    ),
                tint = Color.White,
                modifier = Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape).padding(2.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.18f))
                .testTag(FeedPlaybackTags.PROGRESS)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progressValue, 0f..1f) },
        ) {
            Box(Modifier.fillMaxWidth(progressValue).height(2.dp).background(Color.White.copy(alpha = 0.85f)))
        }
    }
}

object FeedPlaybackTags {
    const val SURFACE = "tiktok_video_surface"
    const val MUTE = "tiktok_video_mute"
    const val PROGRESS = "tiktok_video_progress"
}

/**
 * Restore a page's manual choice only while its recorded policy still matches. Keep one saved
 * slot per choice: policy-specific keys leave unconsumed historical values in restored pages.
 * Only this state holder is keyed, so audio policy updates never replace the live decoder.
 */
@Composable
fun rememberFeedPlaybackChoice(
    identity: String,
    choice: String,
    defaultValue: Boolean,
): MutableState<Boolean> =
    key(identity, choice) {
        val policySaver =
            listSaver<Boolean, Boolean>(
                save = { listOf(defaultValue, it) },
                restore = { saved -> if (saved[0] == defaultValue) saved[1] else defaultValue },
            )
        rememberSaveable(defaultValue, stateSaver = policySaver) { mutableStateOf(defaultValue) }
    }
