/*
 * Transport vector path data: Copyright (C) 2017 The Android Open Source Project.
 * Licensed under the Apache License, Version 2.0.
 * https://www.apache.org/licenses/LICENSE-2.0
 * See composeResources/files/licenses/exoplayer_NOTICE.txt and exoplayer_APACHE2.txt.
 */
package com.orbin.uinext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ReaderMediaControlsTags {
    const val PANEL = "reader-media-controls"
    const val BUTTONS = "reader-media-controls-buttons"
    const val TIME_BAR = "reader-media-controls-time-bar"
}

/** Clover 3.0.2's default ExoPlayer 2.9.0 controller geometry, shared by both native players. */
@Composable
fun ReaderMediaControls(
    playing: Boolean,
    positionSeconds: Double,
    durationSeconds: Double,
    onPlayPause: () -> Unit,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    bufferedFraction: Float = 0f,
    onScrubbingChange: (Boolean) -> Unit = {},
    playLabel: String = "Play",
    pauseLabel: String = "Pause",
    restartLabel: String = "Restart",
    rewindLabel: String = "Rewind 5 seconds",
    fastForwardLabel: String = "Forward 15 seconds",
    nextLabel: String = "Next",
) {
    val seekable = durationSeconds.isFinite() && durationSeconds > 0.0
    val duration = if (seekable) durationSeconds else 0.0
    val position = positionSeconds.takeIf { it.isFinite() }?.coerceIn(0.0, duration) ?: 0.0
    // Background may draw behind the navigation bar; the transport and time bar stay in the
    // safe drawing area so they are not covered on edge-to-edge displays.
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            ).testTag(ReaderMediaControlsTags.PANEL),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val transportWidth = minOf(71.dp, maxWidth / 5)
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp).testTag(ReaderMediaControlsTags.BUTTONS),
                horizontalArrangement = Arrangement.Center,
            ) {
                MediaTransportButton(
                    MediaTransportIcons.Previous,
                    restartLabel,
                    transportWidth,
                    seekable,
                ) { onSeek(0.0) }
                MediaTransportButton(MediaTransportIcons.Rewind, rewindLabel, transportWidth, seekable) {
                    onSeek((position - 5.0).coerceAtLeast(0.0))
                }
                MediaTransportButton(
                    if (playing || loading) MediaTransportIcons.Pause else MediaTransportIcons.Play,
                    if (playing || loading) pauseLabel else playLabel,
                    transportWidth,
                ) { onPlayPause() }
                MediaTransportButton(MediaTransportIcons.FastForward, fastForwardLabel, transportWidth, seekable) {
                    onSeek((position + 15.0).coerceAtMost(duration))
                }
                MediaTransportButton(MediaTransportIcons.Next, nextLabel, transportWidth, enabled = false) {}
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaTime(position)
            MediaTimeBar(
                fraction = if (seekable) (position / duration).toFloat() else 0f,
                bufferedFraction = bufferedFraction,
                enabled = seekable,
                onSeek = { onSeek(it * duration) },
                onScrubbingChange = onScrubbingChange,
                modifier = Modifier.weight(1f),
            )
            MediaTime(duration)
        }
    }
}

@Composable
private fun MediaTransportButton(
    icon: ImageVector,
    label: String,
    width: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier.width(width).height(52.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun MediaTime(seconds: Double) {
    val whole = seconds.toLong().coerceAtLeast(0)
    val hours = whole / 3600
    val minutes = whole / 60 % 60
    val remainder = (whole % 60).toString().padStart(2, '0')
    Text(
        if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$remainder" else "$minutes:$remainder",
        color = Color(0xFFBEBEBE),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun MediaTimeBar(
    fraction: Float,
    bufferedFraction: Float,
    enabled: Boolean,
    onSeek: (Double) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    val seek by rememberUpdatedState(onSeek)
    val scrubbing by rememberUpdatedState(onScrubbingChange)
    Canvas(
        modifier
            .height(48.dp)
            .testTag(ReaderMediaControlsTags.TIME_BAR)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                if (enabled) {
                    setProgress { value ->
                        seek(value.coerceIn(0f, 1f).toDouble())
                        true
                    }
                }
            }.pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures { at ->
                        val inset = 8.dp.toPx()
                        seek(mediaSeekFraction(at.x, size.width.toFloat(), inset))
                    }
                }
            }.pointerInput(enabled) {
                if (enabled) {
                    detectDragGestures(
                        onDragStart = { at ->
                            dragging = true
                            scrubbing(true)
                            val inset = 8.dp.toPx()
                            seek(mediaSeekFraction(at.x, size.width.toFloat(), inset))
                        },
                        onDragEnd = {
                            dragging = false
                            scrubbing(false)
                        },
                        onDragCancel = {
                            dragging = false
                            scrubbing(false)
                        },
                    ) { change, _ ->
                        change.consume()
                        val inset = 8.dp.toPx()
                        seek(mediaSeekFraction(change.position.x, size.width.toFloat(), inset))
                    }
                }
            },
    ) {
        val inset = 8.dp.toPx()
        val width = (size.width - inset * 2).coerceAtLeast(0f)
        val y = size.height / 2
        val start = Offset(inset, y)
        drawLine(Color(0x33FFFFFF), start, Offset(inset + width, y), 4.dp.toPx())
        drawLine(
            Color(0xCCFFFFFF),
            start,
            Offset(inset + width * bufferedFraction.coerceIn(fraction, 1f), y),
            4.dp.toPx(),
        )
        drawLine(Color.White, start, Offset(inset + width * fraction.coerceIn(0f, 1f), y), 4.dp.toPx())
        if (enabled) drawCircle(Color.White, (if (dragging) 8.dp else 6.dp).toPx(), Offset(inset + width * fraction, y))
    }
}

/** Paths and 32dp intrinsic size from the exact APK's ExoPlayer 2.9.0 vector resources (Apache 2.0). */
private object MediaTransportIcons {
    val Play = transportIcon("Play", "M8,5v14l11,-7z")
    val Pause = transportIcon("Pause", "M6,19h4L10,5L6,5v14zM14,5v14h4L18,5h-4z")
    val Previous = transportIcon("Previous", "M6,6h2v12L6,18zM9.5,12l8.5,6L18,6z")
    val Next = transportIcon("Next", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")
    val Rewind = transportIcon("Rewind", "M11,18L11,6l-8.5,6 8.5,6zM11.5,12l8.5,6L20,6l-8.5,6z")
    val FastForward = transportIcon("FastForward", "M4,18l8.5,-6L4,6v12zM13,6v12l8.5,-6L13,6z")
}

private fun transportIcon(
    name: String,
    path: String,
): ImageVector =
    ImageVector
        .Builder(name, 32.dp, 32.dp, MEDIA_TRANSPORT_VIEWPORT, MEDIA_TRANSPORT_VIEWPORT)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.White))
        .build()

private fun mediaSeekFraction(
    x: Float,
    width: Float,
    inset: Float,
): Double = ((x - inset) / (width - inset * 2).coerceAtLeast(1f)).coerceIn(0f, 1f).toDouble()

private const val MEDIA_TRANSPORT_VIEWPORT = 24f
