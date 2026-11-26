package com.orbin.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.orbin.uinext.components.InlinePlayerPool
import com.orbin.uinext.components.rememberInlinePlayerPool
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.defaultRate
import platform.AVFoundation.muted
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.Foundation.NSURL

private class AVInlinePlayers(
    val pool: InlinePlayerPool<AVPlayer>,
) : InlineNativePlayers

/** The pool a [NativePlayer] takes its AVPlayer from, inside a thread screen's inline tile. */
internal val LocalInlineAVPlayers = staticCompositionLocalOf<InlinePlayerPool<AVPlayer>?> { null }

@Composable
internal actual fun rememberInlineNativePlayers(key: Any?): InlineNativePlayers? {
    val pool =
        rememberInlinePlayerPool(
            key = key,
            create = { AVPlayer() },
            reset = ::resetForReuse,
            release = NativePlaybackContinuity::release,
        )
    return remember(pool) { AVInlinePlayers(pool) }
}

@Composable
internal actual fun ProvideInlineNativePlayers(
    players: InlineNativePlayers?,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalInlineAVPlayers provides (players as? AVInlinePlayers)?.pool, content = content)
}

/** A pooled player, or a new one, now playing [url]. */
internal fun InlinePlayerPool<AVPlayer>?.playerFor(url: NSURL): AVPlayer =
    this?.acquire()?.apply { replaceCurrentItemWithPlayerItem(AVPlayerItem(uRL = url)) } ?: AVPlayer(uRL = url)

/** Back to a new player's state: no item loading or loaded, and default rate and audio. */
private fun resetForReuse(player: AVPlayer) {
    NativePlaybackContinuity.release(player)
    player.defaultRate = 1f
    player.muted = false
}
