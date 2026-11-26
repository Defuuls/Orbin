package com.orbin.uinext.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class ThreadInlineMediaScrollTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var scroll: ScrollState
    private lateinit var mediaState: ThreadInlineMediaState
    private val compositions = mutableMapOf<String, Int>()
    private val frameCompositions = mutableMapOf<String, Int>()

    /** Lays out the moved tiles, then gives the election two frames to recompose. */
    private fun settleLayout() {
        compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithTag("tile-first").fetchSemanticsNodes()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun setTiles() {
        compose.setContent {
            val state = rememberThreadInlineMediaState("site/board/thread")
            mediaState = state
            scroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clipToBounds()
                    .verticalScroll(scroll),
            ) {
                listOf("first", "second", "third", "fourth").forEach { id ->
                    ThreadInlineMediaFrame(
                        state,
                        id,
                        enabled = true,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .testTag("tile-$id")
                                // Materialized by the frame's own Box, so this counts each time the frame recomposes.
                                .composed {
                                    SideEffect { frameCompositions[id] = (frameCompositions[id] ?: 0) + 1 }
                                    Modifier
                                },
                    ) { active ->
                        SideEffect { compositions[id] = (compositions[id] ?: 0) + 1 }
                        if (active) Box(Modifier.fillMaxSize().testTag("player-$id"))
                    }
                }
            }
        }
    }

    @Test
    fun scrollingWithoutChangingTheOwnerDoesNotRecomposeTheTiles() {
        setTiles()
        compose.mainClock.advanceTimeBy(THREAD_INLINE_MEDIA_SETTLE_MILLIS)
        compose.onAllNodesWithTag("player-first").assertCountEquals(1)
        val before = compositions.toMap()
        val framesBefore = frameCompositions.toMap()
        // Ten small scroll frames: every tile moves and reports new bounds, the owner stays.
        repeat(10) {
            compose.runOnIdle { scroll.dispatchRawDelta(2f) }
            compose.waitForIdle()
        }
        compose.onAllNodesWithTag("player-first").assertCountEquals(1)
        assertEquals("Scroll frames must not recompose the tiles' frames", framesBefore, frameCompositions.toMap())
        assertEquals("Scroll frames must not recompose the tiles' content", before, compositions.toMap())
    }

    @Test
    fun aPlayerIsBuiltOnlyOnceTheOwnerHasSettled() {
        setTiles()
        compose.mainClock.advanceTimeBy(THREAD_INLINE_MEDIA_SETTLE_MILLIS)
        compose.onAllNodesWithTag("player-first").assertCountEquals(1)
        compose.mainClock.autoAdvance = false
        // Scroll the first tile out and the second fully in (mdpi: one pixel per dp).
        compose.runOnUiThread {
            scroll.dispatchRawDelta(250f)
            Snapshot.sendApplyNotifications()
        }
        settleLayout()
        // The previous owner is released at once; the new one is not built mid-scroll.
        compose.onAllNodesWithTag("player-first").assertCountEquals(0)
        compose.onAllNodesWithTag("player-second").assertCountEquals(0)
        compose.mainClock.advanceTimeBy(THREAD_INLINE_MEDIA_SETTLE_MILLIS + 50)
        compose.onAllNodesWithTag("player-second").assertCountEquals(1)
    }

    @Test
    fun aTappedTileIsBuiltWithoutWaiting() {
        setTiles()
        compose.mainClock.advanceTimeBy(THREAD_INLINE_MEDIA_SETTLE_MILLIS)
        compose.onAllNodesWithTag("player-first").assertCountEquals(1)
        // A quarter of the second tile is visible, enough for a tap to claim it.
        compose.runOnIdle { scroll.dispatchRawDelta(20f) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            mediaState.requestPlay("second")
            Snapshot.sendApplyNotifications()
        }
        settleLayout()
        compose.onAllNodesWithTag("player-first").assertCountEquals(0)
        compose.onAllNodesWithTag("player-second").assertCountEquals(1)
    }
}
