package com.orbin.uinext.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scrolling down a thread past [VIDEOS] inline videos, stopping on each so it plays: how many
 * players are built. A tile builds its player the way the platform players do, in a `remember`
 * keyed to the tile, and gives it up when it stops being active.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class InlinePlayerPoolScrollTest {
    @get:Rule
    val compose = createComposeRule()

    private class FakePlayer(
        val serial: Int,
    ) {
        var source: String? = null
        var released = false
        var resets = 0
    }

    private lateinit var scroll: ScrollState
    private lateinit var mediaState: ThreadInlineMediaState
    private lateinit var pool: InlinePlayerPool<FakePlayer>
    private var built = 0
    private val all = mutableListOf<FakePlayer>()
    private val playedBy = mutableMapOf<String, Int>()
    private var screenShown by mutableStateOf(true)

    private fun newPlayer(): FakePlayer = FakePlayer(++built).also(all::add)

    /** Main before this change: every tile that plays builds a player and releases it after. */
    @Composable
    private fun PerTilePlayer(id: String) {
        val player = remember(id) { newPlayer().apply { source = id } }
        DisposableEffect(player) { onDispose { player.released = true } }
        Box(Modifier.fillMaxSize().testTag("player-$id"))
        playedBy[id] = player.serial
    }

    /** With the pool: the tile takes the screen's spare player and gives it back. */
    @Composable
    private fun PooledPlayer(id: String) {
        val player = remember(id, pool) { pool.acquire().apply { source = id } }
        DisposableEffect(player) { onDispose { pool.recycle(player) } }
        Box(Modifier.fillMaxSize().testTag("player-$id"))
        playedBy[id] = player.serial
    }

    private fun setThread(pooled: Boolean) {
        compose.setContent {
            if (!screenShown) return@setContent
            mediaState = rememberThreadInlineMediaState("site/board/thread")
            pool =
                rememberInlinePlayerPool(
                    key = "site/board/thread",
                    create = ::newPlayer,
                    reset = {
                        it.source = null
                        it.resets++
                    },
                    release = { it.released = true },
                )
            scroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clipToBounds()
                    .verticalScroll(scroll),
            ) {
                for (index in 1..VIDEOS) {
                    val id = "video-$index"
                    ThreadInlineMediaFrame(
                        mediaState,
                        id,
                        enabled = true,
                        modifier = Modifier.fillMaxWidth().height(250.dp),
                    ) { active ->
                        if (active) {
                            if (pooled) PooledPlayer(id) else PerTilePlayer(id)
                        }
                    }
                }
            }
        }
    }

    /** Plays the first video, then scrolls one tile at a time and lets each one settle and play. */
    private fun scrollPastEveryVideo() {
        compose.mainClock.advanceTimeBy(THREAD_INLINE_MEDIA_SETTLE_MILLIS + 50)
        compose.onAllNodesWithTag("player-video-1").assertCountEquals(1)
        for (index in 2..VIDEOS) {
            compose.runOnIdle { scroll.dispatchRawDelta(250f) }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(THREAD_INLINE_MEDIA_SETTLE_MILLIS + 50)
            compose.onAllNodesWithTag("player-video-$index").assertCountEquals(1)
        }
    }

    @Test
    fun aPlayerPerVideoWithoutThePool() {
        setThread(pooled = false)
        scrollPastEveryVideo()
        println("InlinePlayerPoolScrollTest per-tile players built for $VIDEOS videos: $built")
        assertEquals("Each video that played built its own player", VIDEOS, built)
        assertEquals(VIDEOS, playedBy.size)
    }

    @Test
    fun onePlayerIsHandedAlongEveryVideoWithThePool() {
        setThread(pooled = true)
        scrollPastEveryVideo()
        println("InlinePlayerPoolScrollTest pooled players built for $VIDEOS videos: $built")
        assertEquals("All $VIDEOS videos played", VIDEOS, playedBy.size)
        assertEquals("One player served every video", 1, built)
        assertEquals(1, pool.created)
        assertEquals("Reset before each next video took it", VIDEOS - 1, all.single().resets)
        assertTrue("The playing tile holds it", !all.single().released)
    }

    @Test
    fun leavingTheScreenReleasesItsPlayers() {
        setThread(pooled = true)
        scrollPastEveryVideo()
        screenShown = false
        compose.waitForIdle()
        assertTrue("Every player built is released", all.isNotEmpty() && all.all { it.released })
        assertEquals(0, pool.idleCount)
    }

    @Test
    fun aTappedTileStillPlaysAtOnceWithThePool() {
        setThread(pooled = true)
        compose.mainClock.advanceTimeBy(THREAD_INLINE_MEDIA_SETTLE_MILLIS + 50)
        compose.onAllNodesWithTag("player-video-1").assertCountEquals(1)
        // A quarter of the second tile is visible, enough for a tap to claim it.
        compose.runOnIdle { scroll.dispatchRawDelta(20f) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            mediaState.requestPlay("video-2")
            Snapshot.sendApplyNotifications()
        }
        // Lay out, then give the election two frames to recompose; no settle wait.
        compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithTag("player-video-2").fetchSemanticsNodes()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithTag("player-video-1").assertCountEquals(0)
        compose.onAllNodesWithTag("player-video-2").assertCountEquals(1)
    }

    private companion object {
        const val VIDEOS = 10
    }
}
