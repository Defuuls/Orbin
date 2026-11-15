@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.orbin.ios

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID
import kotlin.coroutines.resumeWithException

/** Swift installs the checksum-pinned native FFmpeg backend before the graph starts. */
object NativeMediaConversion {
    private var handler: ((String, String, String, (String?, String?) -> Unit) -> Unit)? = null

    fun setHandler(
        block: (
            operation: String,
            requestId: String,
            sourcePath: String,
            completion: (String?, String?) -> Unit,
        ) -> Unit,
    ) {
        handler = block
    }

    internal suspend fun convert(sourcePath: String): String {
        val native = checkNotNull(handler) { "Media conversion is unavailable in this build." }
        val requestId = NSUUID().UUIDString
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { runCatching { native("cancel", requestId, "") { _, _ -> } } }
            if (!continuation.isActive) return@suspendCancellableCoroutine
            native("convert", requestId, sourcePath) { output, error ->
                if (output != null && error == null) {
                    continuation.resume(output) { _, unusedPath, _ -> removeUnusedConversion(unusedPath) }
                } else if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException(error ?: "Video conversion failed."))
                }
            }
        }
    }
}

/** The caller owns the converted staging file and must release it after Photos has consumed it. */
internal suspend fun convertWebMForPhotos(sourcePath: String): String = NativeMediaConversion.convert(sourcePath)

private fun removeUnusedConversion(path: String) {
    // Cancellation may arrive after native conversion completed but before Kotlin resumed.
    if (path.endsWith("/converted.mp4") && path.contains("/Orbin/MediaExport/")) {
        NSFileManager.defaultManager.removeItemAtPath(path.substringBeforeLast('/'), error = null)
    }
}
