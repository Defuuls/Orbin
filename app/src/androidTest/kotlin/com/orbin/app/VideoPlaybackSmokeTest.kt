package com.orbin.app

import android.Manifest
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
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

    @After
    fun removeFixture() {
        fixture?.delete()
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
        composeRule.waitUntil(TIMEOUT_MS) { playerMatches { !it.isPlaying && !it.playWhenReady } }

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
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
        composeRule.waitUntil(TIMEOUT_MS) {
            playerMatches {
                it.playbackState ==
                    androidx.media3.common.Player.STATE_ENDED
            }
        }
        showControls("Play")
        composeRule.onNodeWithContentDescription("Play").performClick()
        composeRule.waitUntil(TIMEOUT_MS) {
            playerMatches { it.isPlaying && it.currentPosition > 0L }
        }
        assertFalse("Restart must not report a decoder error", playerMatches { it.playerError != null })
    }

    @Test
    fun webmRendersVideoFramesAndCompletes() {
        showRealPlayer(autoPlay = true, loop = false, assetName = "playback-smoke.webm")
        awaitRenderedPlayback()
        composeRule.waitUntil(TIMEOUT_MS) {
            playerMatches { it.playbackState == androidx.media3.common.Player.STATE_ENDED }
        }
        assertFalse("WebM must not report a decoder error", playerMatches { it.playerError != null })
    }

    private fun showRealPlayer(
        autoPlay: Boolean,
        loop: Boolean,
        assetName: String = "playback-smoke.mp4",
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
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                NextTheme {
                    VideoPlayer(
                        url = file.toURI().toString(),
                        autoPlay = autoPlay,
                        muted = true,
                        loop = loop,
                        modifier = Modifier.fillMaxSize().testTag(SURFACE_TAG),
                    )
                }
            }
        }
        composeRule.waitUntil(TIMEOUT_MS) {
            var found: ExoPlayer? = null
            instrumentation.runOnMainSync { found = findPlayer(composeRule.activity.window.decorView) }
            player = found
            found != null
        }
    }

    private fun awaitRenderedPlayback() {
        composeRule.waitUntil(TIMEOUT_MS) {
            playerMatches { current ->
                current.videoDecoderCounters?.ensureUpdated()
                current.isPlaying &&
                    current.currentPosition > MIN_PROGRESS_MS &&
                    (current.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 0
            }
        }
    }

    private fun showControls(label: String) {
        if (composeRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag(SURFACE_TAG).performTouchInput { click() }
        }
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
        }
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
