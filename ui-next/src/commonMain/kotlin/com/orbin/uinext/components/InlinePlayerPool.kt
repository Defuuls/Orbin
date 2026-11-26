package com.orbin.uinext.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Players a thread screen hands from one inline video tile to the next.
 *
 * Only the elected tile plays, so the screen needs one player at a time. Rather than build a new
 * one each time playback moves to another video, a tile that stops gives its player back, reset
 * to empty, and the next tile takes it. At most [spares] idle players are kept; the rest, and every
 * idle player once the screen leaves, are released. Main thread only, like the players themselves.
 *
 * Only iOS pools its AVPlayers. Android builds a player per tile and releases it off the frame
 * instead: sharing one ExoPlayer made a disposed tile's surface detach wait behind the next
 * tile's codec setup on the shared playback thread, and its reset freed the codec anyway.
 */
class InlinePlayerPool<P : Any>(
    private val create: () -> P,
    private val reset: (P) -> Unit,
    private val release: (P) -> Unit,
    private val spares: Int = 1,
) {
    private val idle = ArrayDeque<P>()
    private var closed = false

    /** How many players this pool has built; tests compare it with the tiles that played. */
    var created: Int = 0
        private set

    val idleCount: Int get() = idle.size

    /** An idle player, or a new one when none is spare. */
    fun acquire(): P = idle.removeLastOrNull() ?: create().also { created++ }

    /** Takes back a player its tile no longer shows; it is reset before another tile gets it. */
    fun recycle(player: P) {
        if (closed || idle.size >= spares || player in idle) {
            if (player !in idle) release(player)
            return
        }
        val clean = runCatching { reset(player) }.isSuccess
        if (clean) idle.addLast(player) else release(player)
    }

    /** Releases the idle players; anything recycled later is released at once. */
    fun releaseAll() {
        closed = true
        while (idle.isNotEmpty()) release(idle.removeLast())
    }
}

/** One pool per [key] (a thread screen), released when the screen leaves composition. */
@Composable
fun <P : Any> rememberInlinePlayerPool(
    key: Any?,
    create: () -> P,
    reset: (P) -> Unit,
    release: (P) -> Unit,
): InlinePlayerPool<P> {
    val currentCreate = rememberUpdatedState(create)
    val currentReset = rememberUpdatedState(reset)
    val currentRelease = rememberUpdatedState(release)
    val pool =
        remember(key) {
            InlinePlayerPool(
                create = { currentCreate.value() },
                reset = { currentReset.value(it) },
                release = { currentRelease.value(it) },
            )
        }
    DisposableEffect(pool) { onDispose { pool.releaseAll() } }
    return pool
}
