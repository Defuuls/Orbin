package com.orbin.ios

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVAssetImageGeneratorSucceeded
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.cancelLoading
import platform.AVFoundation.valueWithCMTime
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSData
import platform.Foundation.NSLock
import platform.Foundation.NSURL
import platform.Foundation.NSValue
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

/** One asynchronous native extraction; cancellation must stop its asset's outstanding loads. */
internal interface VideoFrameExtraction {
    fun start(completion: (Result<ImageBitmap?>) -> Unit)

    fun cancel()
}

/** Videos' first frames, decoded once and kept for the session, newest kept when full. */
internal object VideoFrames {
    private val frames = LinkedHashMap<String, ImageBitmap>()
    private val framesMutex = NSLock()

    fun cached(url: String): ImageBitmap? = framesMutex.withLock { frames[url] }

    /** The frame for [url], or null if it can't be read (a broken file, no network). */
    suspend fun load(
        url: String,
        createExtraction: (String) -> VideoFrameExtraction? = ::nativeFrameExtraction,
    ): ImageBitmap? {
        framesMutex.withLock { frames[url] }?.let { return it }
        return iosVideoLoadGate.withPermit {
            // Another request may have filled the cache while this one waited for its turn.
            framesMutex.withLock { frames[url] }?.let { return@withPermit it }
            val frame =
                withContext(Dispatchers.IO) {
                    createExtraction(url)?.let { decode(it) }
                } ?: return@withPermit null
            framesMutex.withLock {
                frames[url] = frame
                while (frames.size > MAX_FRAMES) frames.remove(frames.keys.first())
            }
            frame
        }
    }

    private suspend fun decode(extraction: VideoFrameExtraction): ImageBitmap? {
        val requestLock = NSLock()
        var closed = false

        fun close() =
            requestLock.withLock {
                if (!closed) {
                    closed = true
                    extraction.cancel()
                }
            }

        return try {
            suspendCancellableCoroutine { continuation ->
                // Cancel native loading before returning the permit to another visible tile.
                continuation.invokeOnCancellation { close() }
                requestLock.withLock {
                    if (continuation.isActive) {
                        extraction.start { continuation.resumeWith(it) }
                    }
                }
            }
        } finally {
            close()
        }
    }

    private const val MAX_FRAMES = 60
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun nativeFrameExtraction(url: String): VideoFrameExtraction? =
    // Pools cannot span suspension: completion runs on AVFoundation's own callback queue.
    autoreleasepool {
        val asset = AVURLAsset(uRL = NSURL.URLWithString(url) ?: return@autoreleasepool null, options = null)
        val generator =
            AVAssetImageGenerator(asset).apply {
                appliesPreferredTrackTransform = true
                maximumSize = CGSizeMake(MAX_EDGE, MAX_EDGE)
            }
        object : VideoFrameExtraction {
            override fun start(completion: (Result<ImageBitmap?>) -> Unit) {
                autoreleasepool {
                    val time = CMTimeMakeWithSeconds(FIRST_FRAME_SECONDS, preferredTimescale = TIMESCALE)
                    generator.generateCGImagesAsynchronouslyForTimes(
                        listOf(NSValue.valueWithCMTime(time)),
                    ) { _, cgImage, _, result, _ ->
                        completion(
                            runCatching {
                                autoreleasepool {
                                    if (result != AVAssetImageGeneratorSucceeded || cgImage == null) {
                                        null
                                    } else {
                                        // AVFoundation lends this image only until the callback returns.
                                        // Convert it here without releasing its borrowed reference.
                                        UIImageJPEGRepresentation(UIImage.imageWithCGImage(cgImage), JPEG_QUALITY)
                                            ?.toByteArray()
                                            ?.let { Image.makeFromEncoded(it).toComposeImageBitmap() }
                                    }
                                }
                            },
                        )
                    }
                }
            }

            override fun cancel() {
                autoreleasepool {
                    generator.cancelAllCGImageGeneration()
                    asset.cancelLoading()
                }
            }
        }
    }

private const val MAX_EDGE = 720.0
private const val FIRST_FRAME_SECONDS = 0.1
private const val TIMESCALE = 600
private const val JPEG_QUALITY = 0.85

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
