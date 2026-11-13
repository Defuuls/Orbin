package com.orbin.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// The Android target only runs this module's tests and never shows the viewer, so it has no player.
@Composable
internal actual fun NativePlayer(
    url: String,
    active: Boolean,
    loop: Boolean,
    modifier: Modifier,
    autoPlay: Boolean,
    initiallyMuted: Boolean,
    preloader: NativeMediaPreloader?,
    feedMode: Boolean,
    onFeedLike: (() -> Unit)?,
    onPlayIntentChange: ((Boolean) -> Unit)?,
    onMuteIntentChange: ((Boolean) -> Unit)?,
    playbackSpeed: Float,
    onDoubleTapSave: (() -> Unit)?,
    onPlaybackReadyChange: ((Boolean) -> Unit)?,
) {
    Box(modifier)
}

internal actual val supportsWebM: Boolean = false

@Composable
internal actual fun NativeWebMPlayer(
    url: String,
    modifier: Modifier,
    active: Boolean,
    autoPlay: Boolean,
    initiallyMuted: Boolean,
    loop: Boolean,
    preloader: NativeMediaPreloader?,
    feedMode: Boolean,
    onFeedLike: (() -> Unit)?,
    onPlayIntentChange: ((Boolean) -> Unit)?,
    onMuteIntentChange: ((Boolean) -> Unit)?,
    playbackSpeed: Float,
    onDoubleTapSave: (() -> Unit)?,
    onPlaybackReadyChange: ((Boolean) -> Unit)?,
) {
    Box(modifier)
}

@Composable
internal actual fun NativeInlineLoop(
    url: String,
    modifier: Modifier,
    loop: Boolean,
    muted: Boolean,
) {
    Box(modifier)
}

@Composable
internal actual fun NativeVideoFrame(
    url: String,
    modifier: Modifier,
) {
    Box(modifier)
}
