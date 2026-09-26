package com.orbin.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// The Android target only runs this module's tests and never shows the viewer, so it has no player.
@Composable
internal actual fun NativePlayer(
    url: String,
    active: Boolean,
    modifier: Modifier,
) {
    Box(modifier)
}
