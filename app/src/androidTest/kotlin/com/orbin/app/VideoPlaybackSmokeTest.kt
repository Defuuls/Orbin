package com.orbin.app

import android.Manifest
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.orbin.media.video.VideoPlayer
import com.orbin.uinext.FeedPlaybackTags
import com.orbin.uinext.NextTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Exercises the real player, Hilt data source, Android decoder, controls, and Activity lifecycle. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Suppress("UnsafeOptInUsageError")
class VideoPlaybackSmokeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val notificationPermission: TestRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            TestRule { base, _ -> base }
        }

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private var fixture: File? = null
    private var player: ExoPlayer? = null
    private var recreatePlayer: (() -> Unit)? = null

    @After
    fun removeFixture() {
        if (fixture != null) {
            // Dispose the real player before removing its source, including on assertion failure.
            composeRule.runOnUiThread { composeRule.activity.setContent {} }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            player = null
            recreatePlayer = null
            fixture?.delete()
            fixture = null
        }
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun manualPlayRendersFramesAndManualPauseSurvivesBackgrounding() {
        showRealPlayer(autoPlay = false, loop = true)
        composeRule.onNodeWithContentDescription("Play").performClick()
        awaitRenderedPlayback()

        // The player deliberately hides chrome when Play is pressed. Reveal the actual controls
        // before pausing, rather than mutating ExoPlayer directly from the test.
        showControls("Pause")
        composeRule.onNodeWithContentDescription("Pause").performClick()
        assertTrue("Pause updates the native player immediately", playerMatches { !it.isPlaying && !it.playWhenReady })

        // Background before another Compose frame: a stale auto-enter flag would leave this
        // Activity in PiP at PAUSED instead of reaching CREATED.
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue("A manual pause must survive background/foreground", playerMatches { !it.playWhenReady })

        showControls("Play")
        composeRule.onNodeWithContentDescription("Play").performClick()
        awaitRenderedPlayback()
        assertFalse("Playback must not report a decoder error", playerMatches { it.playerError != null })
    }

    @Test
    fun aFinishedNonLoopingClipRestartsWithOnePlayAction() {
        showRealPlayer(autoPlay = true, loop = false)
        awaitRenderedPlayback()
        waitForPlayer { it.playbackState == androidx.media3.common.Player.STATE_ENDED }
        showControls("Play")
        composeRule.onNodeWithContentDescription("Play").performClick()
        waitForPlayer { it.isPlaying && it.currentPosition > 0L }
        assertFalse("Restart must not report a decoder error", playerMatches { it.playerError != null })
    }

    @Test
    fun webmRendersVideoFramesAndCompletes() {
        showRealPlayer(autoPlay = true, loop = false, assetName = "playback-smoke.webm")
        awaitRenderedPlayback()
        waitForPlayer { it.playbackState == androidx.media3.common.Player.STATE_ENDED }
        assertFalse("WebM must not report a decoder error", playerMatches { it.playerError != null })
    }

    @Test
    fun feedPlaybackPausesDirectlyAndNeverEnablesAutomaticPip() {
        showRealPlayer(autoPlay = false, loop = true, feedMode = true)
        composeRule.onNodeWithTag(FeedPlaybackTags.SURFACE).performClick()
        awaitRenderedPlayback()

        composeRule.onNodeWithTag(FeedPlaybackTags.MUTE).performClick()
        waitForPlayer { it.volume == 1f }
        val previousPlayer = checkNotNull(player)
        composeRule.runOnUiThread { checkNotNull(recreatePlayer).invoke() }
        awaitPlayer(previousPlayer)
        awaitRenderedPlayback()
        assertTrue("A recreated feed decoder retains the explicit unmute", playerMatches { it.volume == 1f })

        // An actively playing feed must background normally rather than entering PiP.
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.runOnUiThread {
            assertFalse(
                "Playing feed video must not enter PiP when backgrounded",
                composeRule.activity.isInPictureInPictureMode,
            )
        }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        awaitRenderedPlayback()

        composeRule.onNodeWithTag(FeedPlaybackTags.SURFACE).performTouchInput { click() }
        assertTrue(
            "Feed tap pauses the native player immediately",
            playerMatches { !it.isPlaying && !it.playWhenReady },
        )
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.runOnUiThread {
            assertFalse("A paused feed must remain out of PiP", composeRule.activity.isInPictureInPictureMode)
        }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue("Manual feed pause survives backgrounding", playerMatches { !it.playWhenReady })

        composeRule.onNodeWithTag(FeedPlaybackTags.SURFACE).performClick()
        awaitRenderedPlayback()
        assertFalse("Feed playback must not report a decoder error", playerMatches { it.playerError != null })
    }

    @Test
    fun feedWebmDecodesAndDoubleTapAddsAWatchWithoutPausing() {
        val watches = AtomicInteger()
        showRealPlayer(
            autoPlay = true,
            loop = true,
            assetName = "playback-smoke.webm",
            feedMode = true,
            onFeedLike = { watches.incrementAndGet() },
        )
        awaitRenderedPlayback()
        composeRule.onNodeWithTag(FeedPlaybackTags.SURFACE).performTouchInput { doubleClick() }
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.mainClock.advanceTimeByFrame()
            watches.get() == 1
        }
        awaitRenderedPlayback()
        assertFalse("Feed WebM must not report a decoder error", playerMatches { it.playerError != null })
    }

    private fun showRealPlayer(
        autoPlay: Boolean,
        loop: Boolean,
        assetName: String = "playback-smoke.mp4",
        feedMode: Boolean = false,
        onFeedLike: (() -> Unit)? = null,
    ) {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Welcome to Orbin").fetchSemanticsNodes().isNotEmpty()
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file =
            File.createTempFile(
                "playback-smoke-",
                ".${assetName.substringAfterLast('.')}",
                instrumentation.targetContext.cacheDir,
            )
        instrumentation.context.assets.open(assetName).use { input ->
            file.outputStream().use(input::copyTo)
        }
        fixture = file
        // Video progress and buffering update Compose continuously. Advancing Compose by hand
        // keeps semantics queries from waiting for a looping player to become globally idle;
        // ExoPlayer, Android's decoder and their media clock continue in real time.
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                NextTheme {
                    // The host keeps user intent while the active page releases its decoder.
                    var retainedMuted by rememberSaveable { mutableStateOf(true) }
                    var retainedPlaying by rememberSaveable { mutableStateOf(autoPlay) }
                    var generation by remember { mutableIntStateOf(0) }
                    SideEffect { recreatePlayer = { generation++ } }
                    key(generation) {
                        VideoPlayer(
                            url = file.toURI().toString(),
                            autoPlay = if (feedMode) retainedPlaying else autoPlay,
                            muted = retainedMuted,
                            loop = loop,
                            feedMode = feedMode,
                            onFeedLike = onFeedLike,
                            onPlayIntentChange =
                                if (feedMode) {
                                    { retainedPlaying = it }
                                } else {
                                    null
                                },
                            onMuteIntentChange =
                                if (feedMode) {
                                    { retainedMuted = it }
                                } else {
                                    null
                                },
                            modifier = Modifier.fillMaxSize().testTag(SURFACE_TAG),
                        )
                    }
                }
            }
        }
        awaitPlayer()
    }

    private fun awaitPlayer(previous: ExoPlayer? = null) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.mainClock.advanceTimeByFrame()
            var found: ExoPlayer? = null
            instrumentation.runOnMainSync { found = findPlayer(composeRule.activity.window.decorView) }
            player = found
            found != null && found !== previous
        }
    }

    private fun awaitRenderedPlayback() {
        waitForPlayer { current ->
            current.videoDecoderCounters?.ensureUpdated()
            current.isPlaying &&
                current.currentPosition > MIN_PROGRESS_MS &&
                (current.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 0
        }
    }

    private fun showControls(label: String) {
        composeRule.mainClock.advanceTimeByFrame()
        if (composeRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag(SURFACE_TAG).performTouchInput { click() }
        }
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForPlayer(predicate: (ExoPlayer) -> Boolean) {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.mainClock.advanceTimeByFrame()
            playerMatches(predicate)
        }
        // Flush the player's latest callback into controls and lifecycle/PiP effects as well.
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun playerMatches(predicate: (ExoPlayer) -> Boolean): Boolean {
        var matches = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            matches = player?.let(predicate) == true
        }
        return matches
    }

    private fun findPlayer(view: View): ExoPlayer? {
        if (view is PlayerView) return view.player as? ExoPlayer
        if (view is ViewGroup) {
            repeat(view.childCount) { index ->
                findPlayer(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
        const val MIN_PROGRESS_MS = 150L
        const val SURFACE_TAG = "real-video-player"
    }
}
