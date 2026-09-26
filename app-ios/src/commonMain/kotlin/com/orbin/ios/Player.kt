package com.orbin.ios

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.orbin.core.model.MediaAttachment

/**
 * The system player over the file at [url], with its own controls. It plays while [active] (the
 * viewer's current page) and pauses when paged away from or closed.
 */
@Composable
internal expect fun NativePlayer(
    url: String,
    active: Boolean,
    modifier: Modifier = Modifier,
)

/**
 * Whether the viewer can play this file itself. The system player (AVFoundation) handles MP4,
 * QuickTime and the common audio formats, but not WebM, which much of 4chan's video is; those
 * still open in the browser.
 */
internal val MediaAttachment.playsInApp: Boolean
    get() = isPlayable && extension.lowercase().removePrefix(".") in NATIVE_PLAYER_EXTENSIONS

private val NATIVE_PLAYER_EXTENSIONS = setOf("mp4", "m4v", "mov", "mp3", "m4a", "aac", "wav")
