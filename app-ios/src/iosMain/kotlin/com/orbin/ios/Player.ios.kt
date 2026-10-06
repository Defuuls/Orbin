package com.orbin.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.currentTime
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIColor
import platform.UIKit.UIDevice
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UITapGestureRecognizer
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
    val currentActive = rememberUpdatedState(active)
    val tapHandler = remember(player) { PlayerTapHandler(player) }
    DisposableEffect(controller, tapHandler) {
        val doubleTap =
            UITapGestureRecognizer(target = tapHandler, action = NSSelectorFromString("onDoubleTap:")).apply {
                numberOfTapsRequired = 2uL
                cancelsTouchesInView = false
                delaysTouchesBegan = false
                delaysTouchesEnded = false
            }
        controller.view.addGestureRecognizer(doubleTap)
        onDispose { controller.view.removeGestureRecognizer(doubleTap) }
    }
    DisposableEffect(player) {
        val observer =
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = AVPlayerItemDidPlayToEndTimeNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) {
                if (currentLoop.value && currentActive.value) {
                    player.seekToTime(CMTimeMakeWithSeconds(seconds = 0.0, preferredTimescale = SEEK_TIMESCALE))
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
                val escapedUrl = url.escapeHtmlAttribute()
                val html =
                    "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width," +
                        "initial-scale=1,maximum-scale=1\">" +
                        "<style>html,body{margin:0;width:100%;height:100%;" +
                        "background:transparent;overflow:hidden}" +
                        "video{width:100%;height:100%;object-fit:contain}</style>" +
                        "</head><body><video src=\"$escapedUrl\" autoplay muted loop playsinline>" +
                        "</video></body></html>"
                loadHTMLString(string = html, baseURL = null)
            }
        }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.loadHTMLString("", baseURL = null)
        }
    }
    // Display only: taps must reach the card or post underneath, which opens the player.
    UIKitView(factory = { webView }, modifier = modifier, properties = PASS_THROUGH)
}

/** Adds Android's 10-second double-tap seek gesture to AVKit without intercepting its controls. */
@OptIn(ExperimentalForeignApi::class)
private class PlayerTapHandler(
    private val player: AVPlayer,
) : platform.darwin.NSObject() {
    @ObjCAction
    fun onDoubleTap(gesture: UITapGestureRecognizer) {
        val view = gesture.view ?: return
        val width = view.bounds.useContents { size.width }
        if (width <= 0.0) return
        val forward = gesture.locationInView(view).useContents { x } >= width / 2.0
        val current = CMTimeGetSeconds(player.currentTime()).takeIf { it.isFinite() } ?: 0.0
        val target = (current + if (forward) SEEK_SECONDS else -SEEK_SECONDS).coerceAtLeast(0.0)
        player.seekToTime(CMTimeMakeWithSeconds(seconds = target, preferredTimescale = SEEK_TIMESCALE))
        UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
    }
}

private const val SEEK_SECONDS = 10.0
private const val SEEK_TIMESCALE = 600

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun NativeVideoFrame(
    url: String,
    modifier: Modifier,
) {
    val webView =
        remember(url) {
            val configuration =
                WKWebViewConfiguration().apply {
                    allowsInlineMediaPlayback = true
                }
            WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration).apply {
                // See-through until the frame arrives, so the thumbnail underneath fills the space.
                opaque = false
                backgroundColor = UIColor.clearColor
                scrollView.backgroundColor = UIColor.clearColor
                scrollView.scrollEnabled = false
                scrollView.bounces = false
                userInteractionEnabled = false
                // The media fragment seeks just past zero, which makes WebKit decode and show the
                // first frame without playing; preload stops it fetching much beyond that.
                val src = "${url.escapeHtmlAttribute()}#t=0.001"
                val html =
                    "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width," +
                        "initial-scale=1,maximum-scale=1\">" +
                        "<style>html,body{margin:0;width:100%;height:100%;" +
                        "background:transparent;overflow:hidden}" +
                        "video{width:100%;height:100%;object-fit:cover}</style>" +
                        "</head><body><video src=\"$src\" muted playsinline preload=\"metadata\">" +
                        "</video></body></html>"
                loadHTMLString(string = html, baseURL = null)
            }
        }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.loadHTMLString("", baseURL = null)
        }
    }
    // Display only: taps must reach the card or post underneath, which opens the player.
    UIKitView(factory = { webView }, modifier = modifier, properties = PASS_THROUGH)
}

/** A native view that only draws; touches fall through to the Compose content behind it. */
@OptIn(ExperimentalComposeUiApi::class)
private val PASS_THROUGH = UIKitInteropProperties(interactionMode = null)

private fun String.escapeHtmlAttribute(): String =
    replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
