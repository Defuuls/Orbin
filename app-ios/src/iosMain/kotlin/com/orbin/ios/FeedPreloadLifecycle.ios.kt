package com.orbin.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState.UIApplicationStateActive
import platform.UIKit.UIApplicationWillResignActiveNotification

/** The mutable state is read at dispatch, so an already queued preparation cannot revive a background load. */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun FeedPreloadLifecycle(
    preloader: NativeMediaPreloader,
    target: MediaPreloadKey?,
): Boolean {
    val foreground =
        remember(preloader) {
            mutableStateOf(
                UIApplication.sharedApplication.applicationState == UIApplicationStateActive,
            )
        }
    LaunchedEffect(preloader, target, foreground.value) {
        preloader.prepare(target.takeIf { foreground.value })
    }
    DisposableEffect(preloader) {
        val center = NSNotificationCenter.defaultCenter
        val resign =
            center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) {
                foreground.value = false
                preloader.prepare(null)
            }
        val background =
            center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) {
                foreground.value = false
                preloader.prepare(null)
            }
        val active =
            center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) {
                foreground.value = true
            }
        onDispose {
            center.removeObserver(resign)
            center.removeObserver(background)
            center.removeObserver(active)
            preloader.prepare(null)
        }
    }
    return foreground.value
}
