@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.orbin.ios

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.usePinned
import platform.AVFoundation.defaultRate
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSRunLoop
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.Foundation.writeToFile
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.childViewControllers
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Native speed changes reuse the real decoder and preserve pause intent. */
class PlayerSpeedHostTest {
    @Test
    fun playbackSpeedChangesKeepTheSamePlayerAndNeverResumeAPausedClip() {
        val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".mp4"
        val bytes = Base64.decode(PlaybackFixtures.RED)
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        assertTrue(data.writeToFile(path, atomically = true))
        val url = assertNotNull(NSURL.fileURLWithPath(path).absoluteString)
        val speed = mutableStateOf(1f)
        val visible = mutableStateOf(true)
        val root =
            ComposeUIViewController(configure = { enforceStrictPlistSanityCheck = false }) {
                if (visible.value) {
                    NativePlayer(
                        url = url,
                        active = true,
                        autoPlay = false,
                        loop = true,
                        playbackSpeed = speed.value,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        try {
            withWindow(root, disposeContent = { visible.value = false }) {
                await { root.players().size == 1 }
                val player = assertNotNull(root.players().single().player)
                speed.value = 1.25f
                await { player.defaultRate == 1.25f }
                assertEquals(0f, player.rate, "A rate choice must not start a paused player")
                player.play()
                await { player.rate == 1.25f }
                speed.value = 2f
                await { player.rate == 2f }
                assertEquals(
                    player.objcPtr(),
                    root
                        .players()
                        .single()
                        .player
                        ?.objcPtr(),
                    "Rate changes reuse the decoder",
                )
                player.pause()
                speed.value = 1f
                await { player.defaultRate == 1f }
                assertEquals(0f, player.rate, "Changing speed preserves a manual pause")
            }
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        }
    }

    private fun withWindow(
        root: UIViewController,
        disposeContent: () -> Unit,
        block: () -> Unit,
    ) {
        val originalMute = SessionAudio.muted
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 390.0, 844.0))
        window.rootViewController = root
        window.makeKeyAndVisible()
        try {
            block()
        } finally {
            try {
                disposeContent()
                await { root.players().isEmpty() }
            } finally {
                window.hidden = true
                window.rootViewController = null
                SessionAudio.muted = originalMute
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
        }
        assertTrue(condition(), "Native player did not reach the expected state within five seconds")
    }

    private fun UIViewController.players(): List<AVPlayerViewController> =
        childViewControllers.filterIsInstance<UIViewController>().flatMap {
            if (it is AVPlayerViewController) listOf(it) else it.players()
        }
}
