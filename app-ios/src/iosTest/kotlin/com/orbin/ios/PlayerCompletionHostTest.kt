@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.orbin.ios

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.WebKit.WKWebView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerCompletionHostTest {
    /** The latest state read from WebKit, reported when a wait times out. */
    private var lastSnapshot: MediaSnapshot? = null

    @Test
    fun completedGalleryWebMStaysEndedAcrossForegroundingAndCanReplay() {
        val webMCase = "PlayerCompletionHostTest.completedGalleryWebMStaysEndedAcrossForegroundingAndCanReplay"
        if (!requireWebMDecodeRuntime(webMCase)) return
        val visible = mutableStateOf(true)
        val autoplay = mutableStateOf(true)
        val reportedIntents = mutableListOf<Boolean>()
        var completedEvents = 0
        val root =
            ComposeUIViewController(configure = { enforceStrictPlistSanityCheck = false }) {
                if (visible.value) {
                    NativeWebMPlayer(
                        url = "data:video/webm;base64," + PlaybackFixtures.WEBM,
                        active = true,
                        autoPlay = autoplay.value,
                        initiallyMuted = true,
                        loop = false,
                        feedMode = false,
                        onPlayIntentChange = { reportedIntents += it },
                        onPlaybackEnded = { completedEvents++ },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 390.0, 844.0))
        window.rootViewController = root
        window.makeKeyAndVisible()
        try {
            await("mount the WebM view") { root.view.webViews().size == 1 }
            val webView = root.view.webViews().single()
            await("start autoplay") { webView.mediaSnapshot()?.let { it.position > 0.05 && !it.paused } == true }
            await("play to the end") { webView.mediaSnapshot()?.ended == true }
            // Allow the regular controls poll to reconcile completion before any lifecycle event.
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.35))

            val center = NSNotificationCenter.defaultCenter
            center.postNotificationName(UIApplicationWillResignActiveNotification, `object` = null)
            center.postNotificationName(UIApplicationDidEnterBackgroundNotification, `object` = null)
            center.postNotificationName(UIApplicationDidBecomeActiveNotification, `object` = null)
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.35))

            val completed = assertNotNull(webView.mediaSnapshot())
            assertTrue(completed.paused && completed.ended, "Foregrounding must leave a completed gallery clip ended")
            assertTrue(completed.position >= completed.duration - 0.05)
            assertEquals(listOf(false), reportedIntents, "Completion should report the stopped intent only once")
            assertEquals(1, completedEvents, "An ended clip emits one auto-advance event, with no foreground replay")

            // A fresh playback request still replays the ended element from its beginning.
            autoplay.value = false
            await("record the stop request") {
                webView.mediaSnapshot()?.let { it.command > completed.command && !it.requestedPlaying } == true
            }
            autoplay.value = true
            await("replay from the beginning") {
                webView.mediaSnapshot()?.let {
                    !it.paused && !it.ended && it.position > 0.05 && it.position < it.duration - 0.5
                } == true
            }
            // Sample WebKit while waiting, so a timeout shows whether the replay stalled or
            // ended without the player reporting it.
            await("emit the replay completion") {
                webView.mediaSnapshot()
                completedEvents == 2
            }
            assertEquals(2, completedEvents, "Explicit replay may emit a fresh completion event")
        } finally {
            try {
                visible.value = false
                await("remove the WebM view") { root.view.webViews().isEmpty() }
            } finally {
                window.hidden = true
                window.rootViewController = null
            }
        }
        verifiedWebMDecodeCase(webMCase)
    }

    private fun WKWebView.mediaSnapshot(): MediaSnapshot? {
        var completed = false
        var result: String? = null
        var failure: String? = null
        evaluateJavaScript(
            """
            (function(){
                var v=document.getElementById('v');if(!v)return null;
                var request=window.__orbinPlaybackRequested;
                return [v.currentTime,v.duration,v.paused?1:0,v.ended?1:0,
                    request?request.command:-1,request&&request.play?1:0].join(',');
            })()
            """.trimIndent(),
        ) { value, error ->
            result = value as? String
            failure = error?.localizedDescription
            completed = true
        }
        await("answer the media query") { completed }
        assertNull(failure, "Query actual WebKit media state")
        val values = result?.split(',')?.map(String::toDouble) ?: return null
        return MediaSnapshot(
            position = values[0],
            duration = values[1],
            paused = values[2] == 1.0,
            ended = values[3] == 1.0,
            command = values[4].toInt(),
            requestedPlaying = values[5] == 1.0,
            // What the player checks before it sends a command or polls (hasPlaybackSurface):
            // without a window or a visible size it skips both, silently, and retries later.
            inWindow = window != null,
            width = bounds.useContents { size.width },
            height = bounds.useContents { size.height },
        ).also { lastSnapshot = it }
    }

    /**
     * Polls for up to five seconds. A failure names the step and the last media state WebKit
     * reported, so a timeout on a slow runner can be told apart from playback that stalled.
     */
    private fun await(
        step: String,
        condition: () -> Boolean,
    ) {
        repeat(100) {
            if (condition()) return
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
        }
        assertTrue(
            condition(),
            "WebKit did not $step within five seconds; last media state: ${lastSnapshot ?: "none reported"}",
        )
    }

    private fun UIView.webViews(): List<WKWebView> =
        subviews.filterIsInstance<UIView>().flatMap {
            if (it is WKWebView) listOf(it) else it.webViews()
        }

    private data class MediaSnapshot(
        val position: Double,
        val duration: Double,
        val paused: Boolean,
        val ended: Boolean,
        val command: Int,
        val requestedPlaying: Boolean,
        val inWindow: Boolean,
        val width: Double,
        val height: Double,
    )
}
