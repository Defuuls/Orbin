@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.orbin.ios

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
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
    @Test
    fun completedGalleryWebMStaysEndedAcrossForegroundingAndCanReplay() {
        if (!supportsWebM) return
        val visible = mutableStateOf(true)
        val autoplay = mutableStateOf(true)
        val reportedIntents = mutableListOf<Boolean>()
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
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 390.0, 844.0))
        window.rootViewController = root
        window.makeKeyAndVisible()
        try {
            await { root.view.webViews().size == 1 }
            val webView = root.view.webViews().single()
            await { webView.mediaSnapshot()?.let { it.position > 0.05 && !it.paused } == true }
            await { webView.mediaSnapshot()?.ended == true }
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

            // A fresh playback request still replays the ended element from its beginning.
            autoplay.value = false
            await {
                webView.mediaSnapshot()?.let { it.command > completed.command && !it.requestedPlaying } == true
            }
            autoplay.value = true
            await {
                webView.mediaSnapshot()?.let {
                    !it.paused && !it.ended && it.position > 0.05 && it.position < it.duration - 0.5
                } == true
            }
        } finally {
            try {
                visible.value = false
                await { root.view.webViews().isEmpty() }
            } finally {
                window.hidden = true
                window.rootViewController = null
            }
        }
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
        await { completed }
        assertNull(failure, "Query actual WebKit media state")
        val values = result?.split(',')?.map(String::toDouble) ?: return null
        return MediaSnapshot(
            position = values[0],
            duration = values[1],
            paused = values[2] == 1.0,
            ended = values[3] == 1.0,
            command = values[4].toInt(),
            requestedPlaying = values[5] == 1.0,
        )
    }

    private fun await(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
        }
        assertTrue(condition(), "WebKit did not reach the expected playback state within five seconds")
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
    )
}
