package com.orbin.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
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
    loop: Boolean,
    modifier: Modifier,
) {
    val player = remember(url) { NSURL.URLWithString(url)?.let { AVPlayer(uRL = it) } } ?: return
    val controller =
        remember(player) {
            AVPlayerViewController().apply {
                this.player = player
                allowsPictureInPicturePlayback = true
                canStartPictureInPictureAutomaticallyFromInline = true
            }
        }
    val currentLoop = rememberUpdatedState(loop)
    DisposableEffect(player) {
        val observer =
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = AVPlayerItemDidPlayToEndTimeNotification,
                `object` = player.currentItem,
                queue = NSOperationQueue.mainQueue,
            ) {
                if (currentLoop.value) {
                    player.seekToTime(CMTimeMake(value = 0, timescale = 1))
                    player.play()
                }
            }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }
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
            val configuration =
                WKWebViewConfiguration().apply {
                    allowsInlineMediaPlayback = true
                    mediaTypesRequiringUserActionForPlayback = 0uL
                }
            WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration).apply {
                scrollView.scrollEnabled = false
                scrollView.bounces = false
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

/** Muted inline preview for a catalog video; the thumbnail remains visible underneath until loaded. */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun NativeInlineLoop(
    url: String,
    modifier: Modifier,
) {
    val webView =
        remember(url) {
            val configuration =
                WKWebViewConfiguration().apply {
                    allowsInlineMediaPlayback = true
                    mediaTypesRequiringUserActionForPlayback = 0uL
                }
            WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration).apply {
                scrollView.scrollEnabled = false
                scrollView.bounces = false
                userInteractionEnabled = false
                val escapedUrl =
                    url.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
                val html =
                    "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1\">" +
                        "<style>html,body{margin:0;width:100%;height:100%;background:transparent;overflow:hidden}video{width:100%;height:100%;object-fit:contain}</style>" +
                        "</head><body><video src=\"$escapedUrl\" autoplay muted loop playsinline></video></body></html>"
                loadHTMLString(htmlString = html, baseURL = null)
            }
        }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.loadHTMLString("", baseURL = null)
        }
    }
    UIKitView(factory = { webView }, modifier = modifier)
}
