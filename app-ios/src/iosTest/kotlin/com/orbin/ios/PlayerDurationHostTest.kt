@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.orbin.ios

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.currentItem
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.status
import platform.CoreMedia.CMTimeGetSeconds
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
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Ready-state metadata checks use a real local AVPlayer, independent of the UI/lifecycle harness. */
class PlayerDurationHostTest {
    @Test
    fun nativeDurationWaitsForReadinessThenStopsQueryingKnownMetadata() {
        val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".mp4"
        val bytes = Base64.decode(PlaybackFixtures.RED)
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        assertTrue(data.writeToFile(path, atomically = true))
        val player = AVPlayer(uRL = NSURL.fileURLWithPath(path))
        try {
            val item = assertNotNull(player.currentItem)
            val duration = ReadyMediaDuration()
            var queries = 0
            assertEquals(0.0, duration.read(ready = false) { error("Preparing item queried metadata") })
            repeat(100) {
                if (item.status != AVPlayerItemStatusReadyToPlay) {
                    NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
                }
            }
            assertEquals(AVPlayerItemStatusReadyToPlay, item.status, "The local native fixture must become ready")
            val first =
                duration.read(ready = true) {
                    queries++
                    CMTimeGetSeconds(item.duration)
                }
            assertTrue(first > 0.0, "The actual ready fixture exposes a finite duration")
            repeat(20) {
                assertEquals(
                    first,
                    duration.read(item.status == AVPlayerItemStatusReadyToPlay) {
                        queries++
                        CMTimeGetSeconds(item.duration)
                    },
                )
            }
            assertEquals(1, queries, "Repeated UI control polls must not repeatedly query AVFoundation duration")
        } finally {
            player.pause()
            player.replaceCurrentItemWithPlayerItem(null)
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        }
    }
}
