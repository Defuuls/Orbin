package com.orbin.ios

import androidx.compose.runtime.Composable

// The Android target only runs this module's tests and has no system player to share.
@Composable
internal actual fun rememberInlineNativePlayers(key: Any?): InlineNativePlayers? = null

@Composable
internal actual fun ProvideInlineNativePlayers(
    players: InlineNativePlayers?,
    content: @Composable () -> Unit,
) {
    content()
}
