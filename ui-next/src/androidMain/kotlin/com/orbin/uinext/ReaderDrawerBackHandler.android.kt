package com.orbin.uinext

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key

@Composable
internal actual fun ReaderDrawerBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // A drawer opening registers after already mounted route handlers. The closed callback is
    // disabled; it cannot consume Back intended for the current route.
    key(enabled) { BackHandler(enabled = enabled, onBack = onBack) }
}
