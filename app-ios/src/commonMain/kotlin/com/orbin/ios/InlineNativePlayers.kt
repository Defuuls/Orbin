package com.orbin.ios

import androidx.compose.runtime.Composable

/**
 * A thread screen's spare system players, shared by its inline videos so that playback moving to
 * the next video reuses a player rather than building one. Null where nothing is pooled.
 */
internal interface InlineNativePlayers

/** One set per [key] (a thread screen), released when the screen leaves composition. */
@Composable
internal expect fun rememberInlineNativePlayers(key: Any?): InlineNativePlayers?

/** [NativePlayer]s composed in [content] take and return their player through [players]. */
@Composable
internal expect fun ProvideInlineNativePlayers(
    players: InlineNativePlayers?,
    content: @Composable () -> Unit,
)
