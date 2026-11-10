package com.orbin.ios

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.asset
import platform.AVFoundation.cancelLoading
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSLog
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIColor
import platform.UIKit.UIDevice
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject
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
    autoPlay: Boolean,
    initiallyMuted: Boolean,
    preloader: NativeMediaPreloader?,
) {
    val player =
        remember(url, preloader) {
            preloader?.takeNative(url)
                ?: NSURL.URLWithString(url)?.let { AVPlayer(uRL = it).apply { muted = initiallyMuted } }
        } ?: return
    var muted by remember(player) { mutableStateOf(player.muted) }
    val currentLoop = rememberUpdatedState(loop)
    val currentActive = rememberUpdatedState(active)
    val foreground = remember(player) { mutableStateOf(true) }
    var playRequested by remember(player) { mutableStateOf(active && autoPlay) }
    var state by remember(player) { mutableStateOf(VideoState()) }
    DisposableEffect(player) {
        val center = NSNotificationCenter.defaultCenter
        val observer =
            center.addObserverForName(
                name = AVPlayerItemDidPlayToEndTimeNotification,
                `object` = player.currentItem,
                queue = NSOperationQueue.mainQueue,
            ) {
                val visibleAndForeground = currentActive.value && foreground.value
                if (currentLoop.value && visibleAndForeground && playRequested) {
                    player.seekToTime(CMTimeMakeWithSeconds(seconds = 0.0, preferredTimescale = SEEK_TIMESCALE))
                    player.play()
                } else if (!currentLoop.value) {
                    playRequested = false
                }
            }
        val resignObserver =
            center.addObserverForName(
                name = UIApplicationWillResignActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) {
                foreground.value = false
                player.pause()
            }
        val backgroundObserver =
            center.addObserverForName(
                name = UIApplicationDidEnterBackgroundNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) {
                foreground.value = false
                player.pause()
            }
        val foregroundObserver =
            center.addObserverForName(
                name = UIApplicationDidBecomeActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) {
                foreground.value = true
            }
        onDispose {
            center.removeObserver(foregroundObserver)
            center.removeObserver(observer)
            center.removeObserver(resignObserver)
            center.removeObserver(backgroundObserver)
        }
    }
    // Audio policy must not change a manually started or paused player's playback state.
    LaunchedEffect(player, initiallyMuted) {
        muted = initiallyMuted && SessionAudio.muted
        player.muted = muted
        val category = if (muted) AVAudioSessionCategoryAmbient else AVAudioSessionCategoryPlayback
        AVAudioSession.sharedInstance().setCategory(category, error = null)
    }
    LaunchedEffect(player, active, autoPlay) {
        playRequested = active && autoPlay
    }
    LaunchedEffect(player, active, playRequested, foreground.value) {
        if (active && playRequested && foreground.value) {
            val duration = player.currentItem?.let { seconds(it.duration) } ?: 0.0
            if (duration > 0.0 && seconds(player.currentTime()) >= duration - 0.1) {
                player.seekToTime(CMTimeMakeWithSeconds(seconds = 0.0, preferredTimescale = SEEK_TIMESCALE))
            }
            player.play()
        } else {
            player.pause()
        }
    }
    LaunchedEffect(player, active) {
        if (!active) return@LaunchedEffect
        var reported = false
        while (true) {
            // Says why a video stays black: decoder exhaustion shows as AVFoundation -11839 or -11819.
            player.currentItem?.error?.takeIf { !reported }?.let {
                reported = true
                NSLog("OrbinVideo AVPlayer failed: %@ %ld", it.domain, it.code)
            }
            state =
                VideoState(
                    playing = player.rate > 0f,
                    position = seconds(player.currentTime()),
                    duration = player.currentItem?.let { seconds(it.duration) } ?: 0.0,
                )
            delay(CONTROLS_REFRESH_MS)
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.pause()
            // Stop the asset's outstanding loads too, so nothing keeps the item's network and
            // decoder alive until Kotlin's collector releases the player object.
            player.currentItem?.asset?.cancelLoading()
            player.replaceCurrentItemWithPlayerItem(null)
        }
    }
    Box(modifier) {
        UIKitViewController(
            factory = {
                AVPlayerViewController().apply {
                    showsPlaybackControls = false
                    allowsPictureInPicturePlayback = true
                }
            },
            // The interop factory runs once per host, not once per remembered player. A URL
            // replacement must attach the player that the playback effects and controls use.
            // Only the page on screen offers picture in picture: neighbours composed by the pager
            // are not PiP sources.
            update = {
                it.player = player
                it.canStartPictureInPictureAutomaticallyFromInline = active
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
            properties = PASS_THROUGH,
        )
        VideoControls(
            state = state.copy(muted = muted),
            onTogglePlay = {
                playRequested = player.rate <= 0f
                if (!playRequested) {
                    player.pause()
                } else {
                    if (state.duration > 0.0 && state.position >= state.duration - 0.1) {
                        player.seekToTime(CMTimeMakeWithSeconds(seconds = 0.0, preferredTimescale = SEEK_TIMESCALE))
                    }
                    player.play()
                }
            },
            onToggleMute = {
                muted = SessionAudio.toggle(muted)
                player.muted = muted
                val category = if (muted) AVAudioSessionCategoryAmbient else AVAudioSessionCategoryPlayback
                AVAudioSession.sharedInstance().setCategory(category, error = null)
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
    active: Boolean,
    autoPlay: Boolean,
    initiallyMuted: Boolean,
    loop: Boolean,
    preloader: NativeMediaPreloader?,
) {
    var muted by remember(url, initiallyMuted) { mutableStateOf(initiallyMuted) }
    var playRequested by remember(url) { mutableStateOf(active && autoPlay) }
    var playCommand by remember(url) { mutableIntStateOf(0) }
    var foreground by remember(url) { mutableStateOf(true) }
    val currentActive by rememberUpdatedState(active)
    val request by rememberUpdatedState(
        WebMPlaybackRequest(playCommand, playRequested && active && foreground, muted, loop),
    )
    val navigation =
        remember(url) {
            object : NSObject(), WKNavigationDelegateProtocol {
                override fun webView(
                    webView: WKWebView,
                    didFinishNavigation: WKNavigation?,
                ) {
                    webView.evaluateJavaScript(webMPlaybackScript(request), completionHandler = null)
                }
            }
        }
    val webView =
        remember(url, preloader) {
            val warmed = preloader?.takeWebM(url)
            if (warmed != null) {
                warmed.navigationDelegate = navigation
                return@remember warmed
            }
            val configuration =
                WKWebViewConfiguration().apply {
                    allowsInlineMediaPlayback = true
                    mediaTypesRequiringUserActionForPlayback = 0uL
                    // In-memory only: the default store keeps WebKit's cache and cookies on disk,
                    // which would undo the app's choice not to persist media (see AppGraph).
                    // One shared store: a new store per view means a new WebContent process per video.
                    websiteDataStore = mediaDataStore
                }
            WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration).apply {
                navigationDelegate = navigation
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
                        "${if (muted) "muted " else ""}" +
                        "${if (loop) "loop " else ""}playsinline>" +
                        "</video></body></html>"
                loadHTMLString(string = html, baseURL = null)
            }
        }
    var state by remember(webView) { mutableStateOf(VideoState()) }
    LaunchedEffect(webView, active, muted) {
        if (active) {
            val category = if (muted) AVAudioSessionCategoryAmbient else AVAudioSessionCategoryPlayback
            AVAudioSession.sharedInstance().setCategory(category, error = null)
        }
    }
    LaunchedEffect(webView, active, autoPlay) {
        playRequested = active && autoPlay
        playCommand++
    }
    // Apply controls immediately, then retry the same command after document/media loading.
    // Repeated polling only reports state once that command has been acknowledged by the page.
    LaunchedEffect(webView, playCommand, muted, loop) {
        val sent = request
        webView.script(webMPlaybackScript(sent))?.let(::parseWebMState)?.let {
            if (playCommand == sent.command) state = it
        }
    }
    LaunchedEffect(webView) {
        while (true) {
            val sent = request
            webView.script(webMPlaybackScript(sent))?.let(::parseWebMState)?.let {
                if (playCommand == sent.command) state = it
            }
            delay(CONTROLS_REFRESH_MS)
        }
    }
    DisposableEffect(webView) {
        val center = NSNotificationCenter.defaultCenter
        val resignObserver =
            center.addObserverForName(
                name = UIApplicationWillResignActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) {
                foreground = false
                playCommand++
                webView.evaluateJavaScript(
                    webMPlaybackScript(request.copy(command = playCommand, play = false, muted = muted)),
                    completionHandler = null,
                )
            }
        val backgroundObserver =
            center.addObserverForName(
                name = UIApplicationDidEnterBackgroundNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) {
                foreground = false
                playCommand++
                webView.evaluateJavaScript(
                    webMPlaybackScript(request.copy(command = playCommand, play = false, muted = muted)),
                    completionHandler = null,
                )
            }
        val foregroundObserver =
            center.addObserverForName(
                name = UIApplicationDidBecomeActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) {
                foreground = true
                playCommand++
                webView.evaluateJavaScript(
                    webMPlaybackScript(request.copy(command = playCommand, play = playRequested && currentActive)),
                    completionHandler = null,
                )
            }
        onDispose {
            center.removeObserver(foregroundObserver)
            center.removeObserver(resignObserver)
            center.removeObserver(backgroundObserver)
            // A neighbouring pager page must not keep playing audio or video.
            webView.navigationDelegate = null
            webView.tearDownMedia()
        }
    }
    Box(modifier) {
        key(webView) {
            UIKitView(factory = { webView }, modifier = Modifier.fillMaxSize(), properties = PASS_THROUGH)
        }
        VideoControls(
            state = state.copy(muted = muted),
            onToggleMute = {
                muted = SessionAudio.toggle(muted)
                val category = if (muted) AVAudioSessionCategoryAmbient else AVAudioSessionCategoryPlayback
                AVAudioSession.sharedInstance().setCategory(category, error = null)
                webView.evaluateJavaScript(
                    webMPlaybackScript(
                        WebMPlaybackRequest(playCommand, playRequested && active && foreground, muted, loop),
                    ),
                    null,
                )
            },
            onTogglePlay = {
                playRequested = !state.playing
                playCommand++
                state = state.copy(playing = playRequested)
                webView.evaluateJavaScript(
                    webMPlaybackScript(
                        WebMPlaybackRequest(playCommand, playRequested && active && foreground, muted, loop),
                    ),
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

/**
 * Lets go of everything a video page holds. Blanking the page alone is not enough: WebKit keeps
 * the previous page, media player and decoder included, in its back-forward cache, and the view
 * itself lives until Kotlin's collector frees the wrapper. Unload the element first, then the page.
 */
internal fun WKWebView.tearDownMedia() {
    evaluateJavaScript(
        "document.querySelectorAll('video').forEach(function(v){v.pause();v.removeAttribute('src');v.load()})",
        null,
    )
    stopLoading()
    loadHTMLString("", baseURL = null)
    // UIKit interop owns attachment/removal of this view. Only stop its media here;
    // Compose may still have a queued interop transaction for the host.
}

/** One in-memory store for every video page, so they share a WebContent process. */
internal val mediaDataStore: WKWebsiteDataStore by lazy { WKWebsiteDataStore.nonPersistentDataStore() }

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
    loop: Boolean,
    muted: Boolean,
) {
    val webView =
        remember(url, loop, muted) {
            val configuration =
                WKWebViewConfiguration().apply {
                    allowsInlineMediaPlayback = true
                    mediaTypesRequiringUserActionForPlayback = 0uL
                    // In-memory only: the default store keeps WebKit's cache and cookies on disk,
                    // which would undo the app's choice not to persist media (see AppGraph).
                    // One shared store: a new store per view means a new WebContent process per video.
                    websiteDataStore = mediaDataStore
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
                        "</head><body><video src=\"$escapedUrl\" autoplay " +
                        "${if (muted) "muted " else ""}${if (loop) "loop " else ""}playsinline>" +
                        "</video></body></html>"
                loadHTMLString(string = html, baseURL = null)
            }
        }
    DisposableEffect(webView) {
        onDispose { webView.tearDownMedia() }
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

internal fun String.escapeHtmlAttribute(): String =
    replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
