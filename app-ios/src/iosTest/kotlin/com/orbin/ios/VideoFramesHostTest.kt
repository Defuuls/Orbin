@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.orbin.ios

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VideoFramesHostTest {
    @Test
    fun cancelledFrameLoadsReleaseBothSlotsBeforeNativeCompletion() =
        runBlocking {
            val identity = NSUUID().UUIDString
            val blocked = List(2) { ControlledExtraction() }
            val blockedUrls = List(2) { "controlled:$identity:$it" }
            val loads =
                blocked.mapIndexed { index, extraction ->
                    async(Dispatchers.Default) { VideoFrames.load(blockedUrls[index]) { extraction } }
                }
            val next = ControlledExtraction()
            val nextUrl = "controlled:$identity:next"
            val visible =
                async(Dispatchers.Default, start = CoroutineStart.LAZY) { VideoFrames.load(nextUrl) { next } }
            try {
                withTimeout(2_000) { blocked.forEach { it.started.await() } }
                visible.start()
                assertNull(
                    withTimeoutOrNull(100) { next.started.await() },
                    "Only two extractions may run before cancellation",
                )
                loads.forEach { it.cancel() }
                withTimeout(2_000) {
                    loads.joinAll()
                    blocked.forEach { it.cancelled.await() }
                    next.started.await()
                }
                assertTrue(loads.all { it.isCancelled })
                blocked.forEach { assertEquals(1, it.cancellations, "Native cancellation runs once") }

                // AVFoundation can still deliver a callback after cancellation. It must not
                // cache the offscreen frame or interfere with the replacement request.
                val frame = ImageBitmap(2, 2)
                blocked.forEach { it.finish(frame) }
                blockedUrls.forEach { assertNull(VideoFrames.cached(it)) }
                next.finish(frame)
                assertSame(frame, withTimeout(2_000) { visible.await() })
                assertSame(frame, VideoFrames.cached(nextUrl))
                assertEquals(1, next.cancellations, "Successful extraction releases native resources too")
            } finally {
                loads.forEach { it.cancel() }
                visible.cancel()
                withTimeout(2_000) { (loads + visible).joinAll() }
            }
        }

    @Test
    fun nativeLocalVideoFrameDecodesAtBoundedSizeAndIsCached() {
        val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".mp4"
        val bytes = Base64.decode(PlaybackFixtures.RED)
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        assertTrue(data.writeToFile(path, atomically = true))
        try {
            val url = assertNotNull(NSURL.fileURLWithPath(path).absoluteString)
            val frame = runBlocking { assertNotNull(withTimeout(5_000) { VideoFrames.load(url) }) }
            assertTrue(frame.width in 1..720 && frame.height in 1..720, "Native extraction preserves its size bound")
            assertSame(frame, VideoFrames.cached(url))
            runBlocking {
                assertSame(frame, VideoFrames.load(url) { error("A cached frame must not open another decoder") })
            }
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        }
    }

    private class ControlledExtraction : VideoFrameExtraction {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        var cancellations = 0
            private set
        private var completion: ((Result<ImageBitmap?>) -> Unit)? = null

        override fun start(completion: (Result<ImageBitmap?>) -> Unit) {
            this.completion = completion
            started.complete(Unit)
        }

        override fun cancel() {
            cancellations++
            cancelled.complete(Unit)
        }

        fun finish(frame: ImageBitmap) {
            checkNotNull(completion).invoke(Result.success(frame))
        }
    }
}
