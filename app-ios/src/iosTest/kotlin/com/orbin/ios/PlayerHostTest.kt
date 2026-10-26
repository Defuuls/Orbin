@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.orbin.ios

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.currentItem
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.WebKit.WKWebView
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Native host tests: these check attachment and layout, not decoded video pixels. */
class PlayerHostTest {
    @Test
    fun nativeHostFollowsUrlReplacementAndReopensWithFreshPlayer() {
        val url = mutableStateOf(FIRST_URL)
        val visible = mutableStateOf(true)
        val root = ComposeUIViewController {
            if (visible.value) {
                NativePlayer(url.value, active = false, loop = true, modifier = Modifier.fillMaxSize())
            }
        }
        withWindow(root) {
            repeat(3) {
                await { root.players().size == 1 }
                val host = root.players().single()
                await { host.player?.currentItem != null }
                val previousPlayer = host.player
                url.value = if (url.value == FIRST_URL) SECOND_URL else FIRST_URL
                await {
                    val asset = host.player?.currentItem?.asset as? AVURLAsset
                    asset?.URL?.absoluteString == url.value
                }
                assertNotSame(previousPlayer, host.player)
                await { host.view.bounds.useContents { size.width > 0 && size.height > 0 } }

                // Navigation away and the app's background lock both remove the composition.
                visible.value = false
                await { root.players().isEmpty() }
                assertNull(host.player)
                visible.value = true
                await { root.players().size == 1 }
                assertNotSame(host, root.players().single())
            }
        }
    }

    @Test
    fun webMUrlReplacementMountsTheNewViewAndRemovesTheOldOne() {
        val url = mutableStateOf(FIRST_URL)
        val visible = mutableStateOf(true)
        val root = ComposeUIViewController {
            if (visible.value) NativeWebMPlayer(url.value, Modifier.fillMaxSize())
        }
        withWindow(root) {
            repeat(3) {
                await { root.view.webViews().size == 1 }
                val previous = root.view.webViews().single()
                url.value = if (url.value == FIRST_URL) SECOND_URL else FIRST_URL
                await { root.view.webViews().singleOrNull()?.let { it !== previous } == true }
                val current = root.view.webViews().single()
                await { current.bounds.useContents { size.width > 0 && size.height > 0 } }
                visible.value = false
                await { root.view.webViews().isEmpty() }
                visible.value = true
                await { root.view.webViews().size == 1 }
                assertNotSame(current, root.view.webViews().single())
            }
        }
    }

    private fun withWindow(root: UIViewController, block: () -> Unit) {
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 390.0, 844.0))
        window.rootViewController = root
        window.makeKeyAndVisible()
        try {
            block()
        } finally {
            window.hidden = true
            window.rootViewController = null
        }
    }

    private fun await(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
        }
        assertTrue(condition(), "Native view did not reach the expected state within five seconds")
    }

    private fun UIViewController.players(): List<AVPlayerViewController> =
        childViewControllers.filterIsInstance<UIViewController>().flatMap {
            if (it is AVPlayerViewController) listOf(it) else it.players()
        }

    private fun UIView.webViews(): List<WKWebView> =
        subviews.filterIsInstance<UIView>().flatMap {
            if (it is WKWebView) listOf(it) else it.webViews()
        }
}

// Local missing files keep host lifecycle coverage independent of network/decoder availability.
private const val FIRST_URL = "file:///tmp/orbin-host-test-first.mp4"
private const val SECOND_URL = "file:///tmp/orbin-host-test-second.mp4"
