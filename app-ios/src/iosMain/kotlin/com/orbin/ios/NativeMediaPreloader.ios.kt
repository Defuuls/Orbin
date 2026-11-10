@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.orbin.ios

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerStatusReadyToPlay
import platform.AVFoundation.asset
import platform.AVFoundation.cancelLoading
import platform.AVFoundation.cancelPendingPrerolls
import platform.AVFoundation.currentItem
import platform.AVFoundation.error
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.preferredForwardBufferDuration
import platform.AVFoundation.prerollAtRate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.status
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.accessibilityElementsHidden
import platform.UIKit.setFrame
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/** Only the upcoming clip is retained; promoted resources become the visible player's responsibility. */
internal actual fun createNativeMediaPreloader(): NativeMediaPreloader = IosMediaPreloader()

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun NativeMediaPreloadHost(preloader: NativeMediaPreloader) {
    val owner = preloader as? IosMediaPreloader ?: return
    UIKitView(
        factory = { MediaPreloadHostView().also(owner::attachHost) },
        onRelease = owner::detachHost,
        modifier = Modifier.size(1.dp),
        properties = UIKitInteropProperties(interactionMode = null),
    )
}

/** A live, clipped pixel behind the gallery; hidden or detached WebKit views can suspend their documents. */
internal class MediaPreloadHostView : UIView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0)) {
    init {
        clipsToBounds = true
        userInteractionEnabled = false
        accessibilityElementsHidden = true
        backgroundColor = UIColor.blackColor
    }
}

internal fun NativeMediaPreloader.takeNative(url: String): AVPlayer? = (this as? IosMediaPreloader)?.takeNative(url)

internal fun NativeMediaPreloader.takeWebM(url: String): WKWebView? = (this as? IosMediaPreloader)?.takeWebM(url)

internal fun NativeMediaPreloader.peekNative(url: String): AVPlayer? = (this as? IosMediaPreloader)?.peekNative(url)

internal fun NativeMediaPreloader.peekWebM(url: String): WKWebView? = (this as? IosMediaPreloader)?.peekWebM(url)

internal fun NativeMediaPreloader.isPrerolled(url: String): Boolean =
    (this as? IosMediaPreloader)?.isPrerolled(url) == true

private class IosMediaPreloader : NativeMediaPreloader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val upcoming = SingleUpcomingMedia<PreparedClip>(create = ::create, release = PreparedClip::close)
    private var warmHost: MediaPreloadHostView? = null
    private var closed = false

    fun attachHost(host: MediaPreloadHostView) {
        if (closed) return
        warmHost = host
        upcoming.key?.let(upcoming::peek)?.let { if (it is PreparedWebM) mount(it) }
    }

    fun detachHost(host: MediaPreloadHostView) {
        if (warmHost != host) return
        warmHost = null
        upcoming.clear()
    }

    override fun prepare(target: MediaPreloadKey?) {
        if (closed) return
        upcoming.prepare(target?.takeIf { NSURL.URLWithString(it.url) != null })
    }

    internal fun peekNative(url: String): AVPlayer? {
        val key = upcoming.key?.takeIf { it.url == url && !it.webM } ?: return null
        return (upcoming.peek(key) as? PreparedNative)?.player
    }

    internal fun isPrerolled(url: String): Boolean {
        val key = upcoming.key?.takeIf { it.url == url && !it.webM } ?: return false
        return (upcoming.peek(key) as? PreparedNative)?.prerolled == true
    }

    internal fun takeNative(url: String): AVPlayer? {
        val key = upcoming.key?.takeIf { it.url == url && !it.webM } ?: return null
        return (upcoming.claim(key) as? PreparedNative)?.promote()
    }

    internal fun peekWebM(url: String): WKWebView? {
        val key = upcoming.key?.takeIf { it.url == url && it.webM } ?: return null
        return (upcoming.peek(key) as? PreparedWebM)?.webView
    }

    internal fun takeWebM(url: String): WKWebView? {
        val key = upcoming.key?.takeIf { it.url == url && it.webM } ?: return null
        val clip = upcoming.claim(key) as? PreparedWebM ?: return null
        if (clip.webView.superview == warmHost) clip.webView.removeFromSuperview()
        return clip.promote()
    }

    override fun close() {
        closed = true
        upcoming.clear()
        warmHost = null
        scope.cancel()
    }

    private fun create(key: MediaPreloadKey): PreparedClip =
        if (key.webM) PreparedWebM(key.url).also(::mount) else PreparedNative(key.url, scope)

    private fun mount(clip: PreparedWebM) {
        warmHost?.let { host ->
            clip.webView.setFrame(CGRectMake(0.0, 0.0, 1.0, 1.0))
            host.addSubview(clip.webView)
        }
    }
}

private sealed interface PreparedClip {
    fun close()
}

private class PreparedNative(
    url: String,
    scope: CoroutineScope,
) : PreparedClip {
    val player =
        AVPlayer(uRL = requireNotNull(NSURL.URLWithString(url))).apply {
            muted = true
            currentItem?.preferredForwardBufferDuration = FORWARD_BUFFER_SECONDS
        }
    var prerolled = false
        private set
    private var transferred = false
    private var closed = false
    private var prepareJob: Job? =
        scope.launch {
            withTimeoutOrNull(PREPARE_TIMEOUT_MS) {
                while (isActive && player.status != AVPlayerStatusReadyToPlay && player.error == null) {
                    delay(READY_POLL_MS)
                }
                if (isActive && player.status == AVPlayerStatusReadyToPlay) {
                    // Preroll decodes/buffers without advancing playback or starting audio.
                    player.prerollAtRate(rate = 1f, completionHandler = { finished ->
                        scope.launch {
                            if (!transferred && !closed) prerolled = finished
                        }
                    })
                }
            }
        }

    fun promote(): AVPlayer {
        transferred = true
        prepareJob?.cancel()
        prepareJob = null
        player.cancelPendingPrerolls()
        return player
    }

    override fun close() {
        closed = true
        prepareJob?.cancel()
        prepareJob = null
        player.cancelPendingPrerolls()
        player.pause()
        player.currentItem?.asset?.cancelLoading()
        player.replaceCurrentItemWithPlayerItem(null)
    }
}

private class PreparedWebM(
    url: String,
) : PreparedClip {
    private var promoted = false
    private val navigation =
        object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(
                webView: WKWebView,
                didFinishNavigation: WKNavigation?,
            ) {
                if (!promoted) {
                    webView.evaluateJavaScript(
                        "var v=document.getElementById('v');if(v){v.muted=true;v.preload='auto';" +
                            "if(v.networkState===0)v.load()}",
                        completionHandler = null,
                    )
                }
            }
        }
    val webView =
        WKWebView(
            frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
            configuration =
                WKWebViewConfiguration().apply {
                    allowsInlineMediaPlayback = true
                    mediaTypesRequiringUserActionForPlayback = 0uL
                    websiteDataStore = mediaDataStore
                },
        ).apply {
            navigationDelegate = navigation
            opaque = false
            backgroundColor = UIColor.blackColor
            scrollView.scrollEnabled = false
            scrollView.bounces = false
            userInteractionEnabled = false
            loadHTMLString(
                "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                    "<style>html,body{margin:0;width:100%;height:100%;background:#000;overflow:hidden}" +
                    "video{width:100%;height:100%;object-fit:contain}</style></head>" +
                    "<body><video id=\"v\" src=\"${url.escapeHtmlAttribute()}\" preload=\"auto\" muted playsinline></video></body></html>",
                baseURL = null,
            )
        }

    fun promote(): WKWebView {
        promoted = true
        webView.navigationDelegate = null
        return webView
    }

    override fun close() {
        webView.navigationDelegate = null
        webView.removeFromSuperview()
        webView.tearDownMedia()
    }
}

private const val FORWARD_BUFFER_SECONDS = 2.0
private const val PREPARE_TIMEOUT_MS = 15_000L
private const val READY_POLL_MS = 25L
