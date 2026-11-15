package com.orbin.ios

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.NSURLCache
import platform.Foundation.distantPast
import platform.WebKit.WKWebsiteDataStore
import kotlin.coroutines.resume

/** Main-thread owners register only their upcoming resources, never their active playback. */
internal object NativeVideoCaches {
    private val clearers = mutableSetOf<() -> Unit>()

    fun register(clear: () -> Unit) {
        clearers += clear
    }

    fun unregister(clear: () -> Unit) {
        clearers -= clear
    }

    fun clearUpcoming() {
        clearers.toList().forEach { it() }
    }
}

internal actual suspend fun nativeVideoCacheUsageBytes(): Long =
    VideoFrames.usageBytes() + NSURLCache.sharedURLCache.currentMemoryUsage.toLong() +
        NSURLCache.sharedURLCache.currentDiskUsage.toLong()

internal actual suspend fun clearNativeVideoCache() =
    withContext(Dispatchers.Main.immediate) {
        VideoFrames.clear()
        NativeVideoCaches.clearUpcoming()
        NSURLCache.sharedURLCache.removeAllCachedResponses()
        suspendCancellableCoroutine<Unit> { continuation ->
            mediaDataStore.removeDataOfTypes(WKWebsiteDataStore.allWebsiteDataTypes(), NSDate.distantPast) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }
