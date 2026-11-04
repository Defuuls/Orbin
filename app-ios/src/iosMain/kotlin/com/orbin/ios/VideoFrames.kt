package com.orbin.ios

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.cancelLoading
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSData
import platform.Foundation.NSLock
import platform.Foundation.NSURL
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

/** Videos' first frames, decoded once and kept for the session, newest kept when full. */
internal object VideoFrames {
    private val frames = LinkedHashMap<String, ImageBitmap>()
    private val framesMutex = NSLock()
    private val decoding = Semaphore(MAX_PARALLEL)

    fun cached(url: String): ImageBitmap? = framesMutex.withLock { frames[url] }

    /** The frame for [url], or null if it can't be read (a broken file, no network). */
    suspend fun load(url: String): ImageBitmap? {
        framesMutex.withLock { frames[url] }?.let { return it }
        val frame = decoding.withPermit { withContext(Dispatchers.IO) { decode(url) } } ?: return null
        framesMutex.withLock {
            frames[url] = frame
            while (frames.size > MAX_FRAMES) frames.remove(frames.keys.first())
        }
        return frame
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun decode(url: String): ImageBitmap? =
        // Kotlin's IO threads never drain an autorelease pool of their own, so without one every
        // decoded frame's UIImage, JPEG data and AVFoundation internals pile up for the session.
        autoreleasepool {
            val asset = AVURLAsset(uRL = NSURL.URLWithString(url) ?: return@autoreleasepool null, options = null)
            val generator =
                AVAssetImageGenerator(asset).apply {
                    appliesPreferredTrackTransform = true
                    maximumSize = CGSizeMake(MAX_EDGE, MAX_EDGE)
                }
            try {
                val cgImage =
                    generator.copyCGImageAtTime(
                        CMTimeMakeWithSeconds(FIRST_FRAME_SECONDS, preferredTimescale = TIMESCALE),
                        actualTime = null,
                        error = null,
                    ) ?: return@autoreleasepool null
                val jpeg = UIImageJPEGRepresentation(UIImage.imageWithCGImage(cgImage), JPEG_QUALITY)
                CGImageRelease(cgImage)
                jpeg?.toByteArray()?.let { Image.makeFromEncoded(it).toComposeImageBitmap() }
            } finally {
                // Give the decoder back now rather than when the collector frees these objects.
                generator.cancelAllCGImageGeneration()
                asset.cancelLoading()
            }
        }

    private const val MAX_PARALLEL = 2
    private const val MAX_FRAMES = 60
    private const val MAX_EDGE = 720.0
    private const val FIRST_FRAME_SECONDS = 0.1
    private const val TIMESCALE = 600
    private const val JPEG_QUALITY = 0.85
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

/** WebM, which AVFoundation can't open. */
internal fun isWebMUrl(url: String): Boolean = url.substringBefore('?').endsWith(".webm", ignoreCase = true)

private inline fun <T> NSLock.withLock(block: () -> T): T {
    lock()
    return try {
        block()
    } finally {
        unlock()
    }
}
