package com.orbin.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIDevice
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

/** AVKit's player view controller, the one Safari and Photos use, hosted in the viewer's page. */
@OptIn(ExperimentalForeignApi::class) // AVAudioSession's NSError out-parameter, left null.
@Composable
internal actual fun NativePlayer(
    url: String,
    active: Boolean,
    modifier: Modifier,
) {
    val player = remember(url) { NSURL.URLWithString(url)?.let { AVPlayer(uRL = it) } } ?: return
    val controller = remember(player) { AVPlayerViewController().apply { this.player = player } }
    LaunchedEffect(player, active) {
        if (active) {
            // Plays with the ringer switch on silent, as video in Safari and Photos does.
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
            player.play()
        } else {
            player.pause()
        }
    }
    DisposableEffect(player) { onDispose { player.pause() } }
    UIKitViewController(factory = { controller }, modifier = modifier)
}

internal actual val supportsWebM: Boolean
    get() {
        val parts =
            UIDevice.currentDevice.systemVersion
                .split('.')
                .mapNotNull(String::toIntOrNull)
        return (parts.firstOrNull() ?: 0) > 17 ||
            ((parts.firstOrNull() ?: 0) == 17 && (parts.getOrNull(1) ?: 0) >= 4)
    }

/** WebKit handles the WebM container on iOS 17.4+, while AVPlayer does not. */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun NativeWebMPlayer(
    url: String,
    modifier: Modifier,
) {
    val webView =
        remember(url) {
            WKWebView(frame = CGRectZero, configuration = WKWebViewConfiguration()).apply {
                NSURL.URLWithString(url)?.let { loadRequest(NSURLRequest(uRL = it)) }
            }
        }
    DisposableEffect(webView) {
        onDispose {
            // A neighbouring pager page must not keep playing audio or video.
            webView.stopLoading()
            webView.loadHTMLString("", baseURL = null)
        }
    }
    UIKitView(factory = { webView }, modifier = modifier)
}
