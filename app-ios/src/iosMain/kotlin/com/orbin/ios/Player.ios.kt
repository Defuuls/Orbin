package com.orbin.ios

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.seekToTime
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.UIKit.UIDevice
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import kotlin.coroutines.resume

/**
 * AVKit's player for the file, drawn without the system's controls: those took every touch, so a
 * vertical swipe could not reach the viewer's pager. [VideoControls] sits over it instead.
 */
@OptIn(ExperimentalForeignApi::class) // AVAudioSession's NSError out-parameter, left null.
@Composable
internal actual fun NativePlayer(
    url: String,
    active: Boolean,
    loop: Boolean,
    modifier: Modifier,
) {
    val player =
        remember(url) {
            NSURL.URLWithString(url)?.let { AVPlayer(uRL = it).apply { muted = SessionAudio.muted } }
        } ?: return
    var muted by remember(player) { mutableStateOf(player.muted) }
    val controller =
        remember(player) {
            AVPlayerViewController().apply {
                this.player = player
                showsPlaybackControls = false
                // Picture in picture, as Android's player: leaving the app while a video plays
                // keeps it going in a floating window. Needs the audio background mode.
                allowsPictureInPicturePlayback = true
                canStartPictureInPictureAutomaticallyFromInline = true
            }
        }
    val currentLoop = rememberUpdatedState(loop)
    val currentActive = rememberUpdatedState(active)
    var state by remember(player) { mutableStateOf(VideoState()) }
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
            // A neighbouring page may have been composed before the last audio choice.
            muted = SessionAudio.muted
            player.muted = muted
            // Plays with the ringer switch on silent, as video in Safari and Photos does.
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
            player.play()
        } else {
            player.pause()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            state =
                VideoState(
                    playing = player.rate > 0f,
                    position = seconds(player.currentTime()),
                    duration = player.currentItem?.let { seconds(it.duration) } ?: 0.0,
                )
            delay(CONTROLS_REFRESH_MS)
        }
    }
    DisposableEffect(player) { onDispose { player.pause() } }
    Box(modifier) {
        UIKitViewController(factory = { controller }, modifier = Modifier.fillMaxSize(), properties = PASS_THROUGH)
        VideoControls(
            state = state.copy(muted = muted),
            onTogglePlay = { if (player.rate > 0f) player.pause() else player.play() },
            onToggleMute = {
                muted = SessionAudio.toggle(muted)
                player.muted = muted
            },
            onSeekTo = { target ->
                player.seekToTime(CMTimeMakeWithSeconds(seconds = target, preferredTimescale = SEEK_TIMESCALE))
            },
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** A media time in seconds, or 0 while it is not yet known. */
@OptIn(ExperimentalForeignApi::class)
private fun seconds(time: CValue<CMTime>): Double = CMTimeGetSeconds(time).takeIf { it.isFinite() } ?: 0.0

internal actual val supportsWebM: Boolean
    get() {
        val parts =
            UIDevice.currentDevice.systemVersion
                .split('.')
                .mapNotNull(String::toIntOrNull)
        return (parts.firstOrNull() ?: 0) > 17 ||
            ((parts.firstOrNull() ?: 0) == 17 && (parts.getOrNull(1) ?: 0) >= 4)
    }

/**
 * WebKit plays the WebM container on iOS 17.4+, which AVPlayer does not. The file plays inline in
 * a page of its own (loaded directly, WebKit would hand it to the system's full-screen player) and
 * the page takes no touches: [VideoControls] drives it through script.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun NativeWebMPlayer(
    url: String,
    modifier: Modifier,
) {
    var muted by remember(url) { mutableStateOf(SessionAudio.muted) }
    val currentMuted = rememberUpdatedState(muted)
    val webView =
        remember(url) {
            val configuration =
                WKWebViewConfiguration().apply {
                    allowsInlineMediaPlayback = true
                    mediaTypesRequiringUserActionForPlayback = 0uL
                }
            WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration).apply {
                opaque = false
                backgroundColor = UIColor.blackColor
                scrollView.scrollEnabled = false
                scrollView.bounces = false
                userInteractionEnabled = false
                val html =
                    "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width," +
                        "initial-scale=1,maximum-scale=1\">" +
                        "<style>html,body{margin:0;width:100%;height:100%;" +
                        "background:#000;overflow:hidden}" +
                        "video{width:100%;height:100%;object-fit:contain}</style>" +
                        "</head><body><video id=\"v\" src=\"${url.escapeHtmlAttribute()}\" " +
                        "autoplay ${if (muted) "muted" else ""} loop playsinline>" +
                        "</video></body></html>"
                loadHTMLString(string = html, baseURL = null)
            }
        }
    var state by remember(webView) { mutableStateOf(VideoState()) }
    LaunchedEffect(webView) {
        while (true) {
            webView.script(webMStateScript(currentMuted.value))?.let(::parseWebMState)?.let { state = it }
            delay(CONTROLS_REFRESH_MS)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            // A neighbouring pager page must not keep playing audio or video.
            webView.stopLoading()
            webView.loadHTMLString("", baseURL = null)
        }
    }
    Box(modifier) {
        UIKitView(factory = { webView }, modifier = Modifier.fillMaxSize(), properties = PASS_THROUGH)
        VideoControls(
            state = state.copy(muted = muted),
            onToggleMute = {
                muted = SessionAudio.toggle(muted)
                webView.evaluateJavaScript(webMStateScript(muted), null)
            },
            onTogglePlay = {
                webView.evaluateJavaScript(
                    "var v=document.getElementById('v');v.paused?v.play():v.pause()",
                    null,
                )
            },
            onSeekTo = { target ->
                webView.evaluateJavaScript("document.getElementById('v').currentTime=$target", null)
            },
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** Runs [script] in the page and gives back its result as text, or null if it had none. */
private suspend fun WKWebView.script(script: String): String? =
    suspendCancellableCoroutine { continuation ->
        evaluateJavaScript(script) { result, _ -> continuation.resume(result as? String) }
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

private const val SEEK_TIMESCALE = 600
private const val CONTROLS_REFRESH_MS = 250L

/**
 * The first frame comes from AVFoundation, not a web view: a WKWebView per card started a web
 * process and a video decoder for every video in view, which is what made long feeds and catalogs
 * crawl. Frames are decoded off the main thread, a few at a time, at card size, and kept in
 * [VideoFrames] so scrolling back is free. WebM, which AVFoundation can't open, keeps its thumbnail.
 */
@Composable
internal actual fun NativeVideoFrame(
    url: String,
    modifier: Modifier,
) {
    if (isWebMUrl(url)) return
    var frame by remember(url) { mutableStateOf(VideoFrames.cached(url)) }
    LaunchedEffect(url) {
        if (frame == null) frame = VideoFrames.load(url)
    }
    frame?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier) }
}

/** A native view that only draws; touches fall through to the Compose content behind it. */
@OptIn(ExperimentalComposeUiApi::class)
private val PASS_THROUGH = UIKitInteropProperties(interactionMode = null)

private fun String.escapeHtmlAttribute(): String =
    replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
