@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.orbin.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.orbin.core.model.MediaType
import kotlinx.cinterop.CValue
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.AVFoundation.AVPlayerItemVideoOutput
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addOutput
import platform.AVFoundation.asset
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.preferredForwardBufferDuration
import platform.AVFoundation.rate
import platform.AVFoundation.removeOutput
import platform.AVKit.AVPlayerViewController
import platform.CoreFoundation.CFRetain
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetPixelFormatType
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSRunLoop
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.Foundation.writeToFile
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.accessibilityElementsHidden
import platform.UIKit.childViewControllers
import platform.WebKit.WKWebView
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Native host and decoder tests; these do not sample composited onscreen pixels. */
class PlayerHostTest {
    @Test
    fun localVideoDecodesAndBecomesDisplayReadyAfterReplacementAndReopen() {
        val paths =
            listOf(PlaybackFixtures.RED, PlaybackFixtures.GREEN).map { encoded ->
                val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".mp4"
                val bytes = Base64.decode(encoded)
                val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
                assertTrue(data.writeToFile(path, atomically = true), "Write generated playback fixture")
                path
            }
        val urls = paths.map { assertNotNull(NSURL.fileURLWithPath(it).absoluteString) }
        val url = mutableStateOf(urls.first())
        val visible = mutableStateOf(true)
        val root =
            ComposeUIViewController(configure = { enforceStrictPlistSanityCheck = false }) {
                if (visible.value) {
                    NativePlayer(
                        url.value,
                        active = true,
                        loop = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        try {
            withWindow(root, disposeContent = { visible.value = false }) {
                SessionAudio.muted = true
                repeat(2) {
                    await { root.players().size == 1 }
                    val host = root.players().single()
                    await { (host.player?.currentItem?.asset as? AVURLAsset)?.URL?.absoluteString == urls[0] }
                    assertDecodedColor(host, red = true)
                    url.value = urls[1]
                    await { (host.player?.currentItem?.asset as? AVURLAsset)?.URL?.absoluteString == urls[1] }
                    assertEquals(host.objcPtr(), root.players().single().objcPtr())
                    assertDecodedColor(host, red = false)
                    visible.value = false
                    await { root.players().isEmpty() }
                    assertNull(host.player)
                    url.value = urls[0]
                    visible.value = true
                }
            }
        } finally {
            paths.forEach { NSFileManager.defaultManager.removeItemAtPath(it, error = null) }
        }
    }

    private fun assertDecodedColor(
        host: AVPlayerViewController,
        red: Boolean,
    ) {
        val player = assertNotNull(host.player)
        val item = assertNotNull(player.currentItem)
        val output =
            AVPlayerItemVideoOutput(
                pixelBufferAttributes =
                    mapOf(
                        CFBridgingRelease(CFRetain(kCVPixelBufferPixelFormatTypeKey)) to
                            kCVPixelFormatType_32BGRA.toLong(),
                    ),
            )
        item.addOutput(output)
        try {
            await {
                val buffer =
                    output.copyPixelBufferForItemTime(player.currentTime(), itemTimeForDisplay = null)
                        ?: return@await false
                try {
                    assertEquals(64uL, CVPixelBufferGetWidth(buffer))
                    assertEquals(64uL, CVPixelBufferGetHeight(buffer))
                    assertEquals(kCVPixelFormatType_32BGRA, CVPixelBufferGetPixelFormatType(buffer))
                    assertEquals(0, CVPixelBufferLockBaseAddress(buffer, 0u))
                    try {
                        val pixels = assertNotNull(CVPixelBufferGetBaseAddress(buffer)).reinterpret<UByteVar>()
                        val offset = (32uL * CVPixelBufferGetBytesPerRow(buffer) + 32uL * 4uL).toInt()
                        val blue = pixels[offset].toInt()
                        val green = pixels[offset + 1].toInt()
                        val actualRed = pixels[offset + 2].toInt()
                        assertTrue(blue < 30, "Decoded frame should have no blue channel")
                        assertTrue(
                            if (red) actualRed > 200 && green < 30 else green > 200 && actualRed < 30,
                            "Decoded frame must match the current URL's color: R=$actualRed G=$green B=$blue",
                        )
                    } finally {
                        CVPixelBufferUnlockBaseAddress(buffer, 0u)
                    }
                    true
                } finally {
                    CVPixelBufferRelease(buffer)
                }
            }
            await {
                if (!host.readyForDisplay) return@await false
                if (host.view.window == null || host.view.hidden || host.view.alpha <= 0.0) return@await false
                val viewWidth = host.view.bounds.useContents { size.width }
                val viewHeight = host.view.bounds.useContents { size.height }
                if (viewWidth <= 0.0 || viewHeight <= 0.0) return@await false
                host.videoBounds.useContents {
                    size.width > 0.0 &&
                        size.height > 0.0 &&
                        origin.x >= -1.0 &&
                        origin.y >= -1.0 &&
                        origin.x + size.width <= viewWidth + 1.0 &&
                        origin.y + size.height <= viewHeight + 1.0
                }
            }
            assertTrue(host.view.window != null && !host.view.hidden && host.view.alpha > 0.0)
            val viewWidth = host.view.bounds.useContents { size.width }
            val viewHeight = host.view.bounds.useContents { size.height }
            assertTrue(viewWidth > 0.0 && viewHeight > 0.0)
            assertTrue(
                host.videoBounds.useContents {
                    size.width > 0.0 &&
                        size.height > 0.0 &&
                        origin.x >= -1.0 &&
                        origin.y >= -1.0 &&
                        origin.x + size.width <= viewWidth + 1.0 &&
                        origin.y + size.height <= viewHeight + 1.0
                },
                "Display-ready video must occupy valid bounds inside its mounted view",
            )
        } finally {
            item.removeOutput(output)
        }
    }

    @Test
    fun aSilentPrerolledClipTransfersToTheVisiblePlayerWithoutReloadingOrBeingReleasedBySettings() {
        val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".mp4"
        val bytes = Base64.decode(PlaybackFixtures.RED)
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        assertTrue(data.writeToFile(path, atomically = true))
        val url = assertNotNull(NSURL.fileURLWithPath(path).absoluteString)
        val key = MediaPreloadKey("upcoming", url, MediaType.VIDEO, "mp4")
        val preloader = createNativeMediaPreloader()
        preloader.prepare(key)
        val warmed = assertNotNull(preloader.peekNative(url))
        try {
            // Actual AVFoundation preroll prepares decode but must remain silent and paused.
            await { preloader.isPrerolled(url) }
            assertEquals(0f, warmed.rate)
            assertTrue(warmed.muted)
            assertEquals(2.0, warmed.currentItem?.preferredForwardBufferDuration)
            val visible = mutableStateOf(true)
            val root =
                ComposeUIViewController(configure = { enforceStrictPlistSanityCheck = false }) {
                    if (visible.value) {
                        NativePlayer(
                            url = url,
                            active = true,
                            autoPlay = false,
                            loop = true,
                            modifier = Modifier.fillMaxSize(),
                            preloader = preloader,
                        )
                    }
                }
            withWindow(root, disposeContent = { visible.value = false }) {
                await { root.players().size == 1 }
                val host = root.players().single()
                val player = assertNotNull(host.player)
                assertEquals(warmed.objcPtr(), player.objcPtr(), "Opening the clip reuses the prerolled native player")
                assertNull(preloader.peekNative(url), "The visible player owns the promoted resource")
                assertEquals(0f, player.rate, "Autoplay off remains paused even after preloading")
                player.play()
                await { secondsForTest(player.currentTime()) > 0.05 }
                assertDecodedColor(host, red = true)
                preloader.prepare(key.copy(id = "next-upcoming"))
                val next = assertNotNull(preloader.peekNative(url))
                assertNotEquals(player.objcPtr(), next.objcPtr())
                preloader.prepare(null)
                assertNull(next.currentItem, "Turning preload off releases only the upcoming player")
                assertNotNull(player.currentItem, "The visible player's transferred item must survive")
                assertTrue(player.rate > 0f)
            }
        } finally {
            preloader.close()
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        }
    }

    @Test
    fun manualPlaybackSurvivesAnAudioPreferenceChangeWhenAutoplayIsOff() {
        val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".mp4"
        val bytes = Base64.decode(PlaybackFixtures.RED)
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        assertTrue(data.writeToFile(path, atomically = true))
        val url = assertNotNull(NSURL.fileURLWithPath(path).absoluteString)
        val muted = mutableStateOf(true)
        val visible = mutableStateOf(true)
        val root =
            ComposeUIViewController(configure = { enforceStrictPlistSanityCheck = false }) {
                if (visible.value) {
                    NativePlayer(
                        url = url,
                        active = true,
                        autoPlay = false,
                        initiallyMuted = muted.value,
                        loop = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        try {
            withWindow(root, disposeContent = { visible.value = false }) {
                await { root.players().size == 1 }
                val player = assertNotNull(root.players().single().player)
                assertEquals(0f, player.rate)
                player.play()
                await { player.rate > 0f }
                muted.value = false
                await { !player.muted }
                assertTrue(player.rate > 0f, "Changing mute must not pause manually started playback")
                player.pause()
                muted.value = true
                await { player.muted }
                assertEquals(0f, player.rate, "Changing mute must also preserve a manual pause")
            }
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
            SessionAudio.muted = true
        }
    }

    @Test
    fun nativeHostFollowsUrlReplacementAndReopensWithFreshPlayer() {
        val url = mutableStateOf(FIRST_URL)
        val visible = mutableStateOf(true)
        val root =
            // The Kotlin test executable has no app Info.plist. The shipped Xcode app supplies
            // CADisableMinimumFrameDurationOnPhone; skip only that display-link check here.
            ComposeUIViewController(configure = { enforceStrictPlistSanityCheck = false }) {
                if (visible.value) {
                    NativePlayer(url.value, active = false, loop = true, modifier = Modifier.fillMaxSize())
                }
            }
        withWindow(root, disposeContent = { visible.value = false }) {
            repeat(3) {
                await { root.players().size == 1 }
                val host = root.players().single()
                await {
                    val asset = host.player?.currentItem?.asset as? AVURLAsset
                    asset?.URL?.absoluteString == url.value
                }
                val previousPlayer = host.player
                SessionAudio.muted = !SessionAudio.muted
                url.value = if (url.value == FIRST_URL) SECOND_URL else FIRST_URL
                await {
                    val asset = host.player?.currentItem?.asset as? AVURLAsset
                    asset?.URL?.absoluteString == url.value
                }
                // UIKit getters may return different Kotlin wrappers for the same native object.
                assertEquals(host.objcPtr(), root.players().single().objcPtr(), "URL replacement reuses the host")
                assertNotEquals(previousPlayer.objcPtr(), host.player.objcPtr())
                assertEquals(SessionAudio.muted, host.player?.muted, "replacement keeps the session audio choice")
                await { host.view.bounds.useContents { size.width > 0 && size.height > 0 } }

                // Navigation away and the app's background lock both remove the composition.
                visible.value = false
                await { root.players().isEmpty() }
                assertNull(host.player)
                visible.value = true
                await { root.players().size == 1 }
                assertNotEquals(host.objcPtr(), root.players().single().objcPtr())
            }
        }
    }

    @Test
    fun preloadedWebMTransfersItsViewAndDecodesRealFramesWhileItsClockAdvances() {
        if (!supportsWebM) return
        val url = "data:video/webm;base64," + PlaybackFixtures.WEBM
        val preloader = createNativeMediaPreloader()
        val visible = mutableStateOf(true)
        val showPlayer = mutableStateOf(false)
        val root =
            ComposeUIViewController(configure = { enforceStrictPlistSanityCheck = false }) {
                if (visible.value) {
                    Box(Modifier.fillMaxSize()) {
                        NativeMediaPreloadHost(preloader)
                        if (showPlayer.value) {
                            NativeWebMPlayer(
                                url = url,
                                active = true,
                                autoPlay = true,
                                initiallyMuted = true,
                                loop = true,
                                preloader = preloader,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        try {
            withWindow(root, disposeContent = { visible.value = false }) {
                // Use the gallery's real live-window host, including prepare-before-host mounting.
                preloader.prepare(MediaPreloadKey("vp9-upcoming", url, MediaType.VIDEO, "webm"))
                val warmed = assertNotNull(preloader.peekWebM(url))
                await { warmed.window != null && root.view.preloadHosts().size == 1 }
                val warmHost = root.view.preloadHosts().single()
                assertEquals(warmHost.objcPtr(), warmed.superview.objcPtr())
                assertTrue(warmed.bounds.useContents { size.width > 0 && size.height > 0 })
                assertTrue(!warmHost.hidden && warmHost.alpha == 1.0)
                assertTrue(warmHost.accessibilityElementsHidden && !warmHost.userInteractionEnabled)
                assertSilentPreparedWebM(warmed)
                showPlayer.value = true
                await {
                    preloader.peekWebM(url) == null &&
                        warmHost.subviews.isEmpty() &&
                        warmed.window != null &&
                        root.view.webViews().size == 1
                }
                val shown = root.view.webViews().single()
                assertEquals(warmed.objcPtr(), shown.objcPtr(), "Opening the clip reuses the prepared WebKit view")
                assertNull(preloader.peekWebM(url))
                var lastSnapshot: List<Double>? = null
                await(failureMessage = {
                    "WebM did not render: state=$lastSnapshot, window=${shown.window != null}, " +
                        "bounds=${shown.bounds.useContents { "${size.width}x${size.height}" }}, $lastWebMDetails"
                }) {
                    lastSnapshot = shown.mediaSnapshot()
                    lastSnapshot?.let {
                        it[0] >= 2 && it[1] == 64.0 && it[2] == 64.0 && it[3] > 0.05 && it[4] > 0
                    } == true
                }
                val playing = assertNotNull(shown.mediaSnapshot())
                assertTrue(playing[3] > 0.0, "The actual HTML video clock advances")
                assertTrue(playing[4] > 0.0, "WebKit reports decoded video frames")
                preloader.prepare(MediaPreloadKey("vp9-following", url, MediaType.VIDEO, "webm"))
                val following = assertNotNull(preloader.peekWebM(url))
                await { following.window != null && root.view.webViews().size == 2 }
                assertNotEquals(shown.objcPtr(), following.objcPtr())
                preloader.prepare(null)
                await { following.window == null && root.view.webViews().size == 1 }
                assertEquals(
                    shown.objcPtr(),
                    root.view
                        .webViews()
                        .single()
                        .objcPtr(),
                )
                assertTrue(warmHost.subviews.isEmpty(), "Promoted WebKit view belongs only to the visible player")
            }
        } finally {
            preloader.close()
        }
    }

    private fun assertSilentPreparedWebM(webView: WKWebView) {
        // The upcoming view loads actual clip metadata without playing or producing audio.
        await { webView.mediaSnapshot()?.let { it[0] >= 1 && it[1] == 64.0 && it[2] == 64.0 } == true }
        val before = assertNotNull(webView.mediaSnapshot())
        assertEquals(0.0, before[3])
        assertEquals(1.0, before[5], "The preloaded view remains paused")
        assertEquals(1.0, before[6], "The preloaded view remains muted")
    }

    @Test
    fun webMUrlReplacementMountsTheNewViewAndRemovesTheOldOne() {
        val url = mutableStateOf(FIRST_URL)
        val visible = mutableStateOf(true)
        val root =
            // The Kotlin test executable has no app Info.plist. The shipped Xcode app supplies
            // CADisableMinimumFrameDurationOnPhone; skip only that display-link check here.
            ComposeUIViewController(configure = { enforceStrictPlistSanityCheck = false }) {
                if (visible.value) NativeWebMPlayer(url.value, Modifier.fillMaxSize())
            }
        withWindow(root, disposeContent = { visible.value = false }) {
            repeat(3) {
                await { root.view.webViews().size == 1 }
                val previous = root.view.webViews().single()
                url.value = if (url.value == FIRST_URL) SECOND_URL else FIRST_URL
                await {
                    root.view
                        .webViews()
                        .singleOrNull()
                        ?.let { it.objcPtr() != previous.objcPtr() } == true
                }
                val current = root.view.webViews().single()
                await { current.bounds.useContents { size.width > 0 && size.height > 0 } }
                visible.value = false
                await { root.view.webViews().isEmpty() }
                visible.value = true
                await { root.view.webViews().size == 1 }
                assertNotEquals(
                    current.objcPtr(),
                    root.view
                        .webViews()
                        .single()
                        .objcPtr(),
                )
            }
        }
    }

    private var lastWebMDetails = ""

    private fun WKWebView.mediaSnapshot(): List<Double>? {
        var completed = false
        var snapshot: String? = null
        var failure: String? = null
        evaluateJavaScript(
            """
            (function(){
                var v=document.getElementById('v');if(!v)return null;
                var frames=v.getVideoPlaybackQuality?v.getVideoPlaybackQuality().totalVideoFrames:(v.webkitDecodedFrameCount||0);
                var r=v.getBoundingClientRect();
                var details={
                    requested:window.__orbinPlaybackRequested,
                    applied:window.__orbinPlaybackApplied,
                    pending:window.__orbinPlaybackPending,
                    rejection:window.__orbinPlaybackLastRejection,
                    error:v.error?{code:v.error.code,message:v.error.message}:null,
                    visibility:document.visibilityState,focused:document.hasFocus(),
                    rect:[r.x,r.y,r.width,r.height],network:v.networkState,
                    duration:v.duration,ended:v.ended,
                    codec:v.canPlayType('video/webm; codecs="vp9"')
                };
                return [v.readyState,v.videoWidth,v.videoHeight,v.currentTime,frames,v.paused?1:0,v.muted?1:0].join(',')+'|'+JSON.stringify(details);
            })()
            """.trimIndent(),
        ) { value, error ->
            snapshot = value as? String
            failure = error?.localizedDescription
            completed = true
        }
        await { completed }
        assertNull(failure, "Query actual WebKit media state")
        val payload = snapshot ?: return null
        lastWebMDetails = payload.substringAfter('|', "")
        return payload.substringBefore('|').split(',').map { it.toDouble() }
    }

    private fun withWindow(
        root: UIViewController,
        disposeContent: () -> Unit,
        block: () -> Unit,
    ) {
        val originalMute = SessionAudio.muted
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 390.0, 844.0))
        window.rootViewController = root
        window.makeKeyAndVisible()
        try {
            block()
        } finally {
            try {
                disposeContent()
                await {
                    root.players().isEmpty() && root.view.webViews().isEmpty() && root.view.preloadHosts().isEmpty()
                }
            } finally {
                window.hidden = true
                window.rootViewController = null
                SessionAudio.muted = originalMute
            }
        }
    }

    private fun await(
        failureMessage: () -> String = { "Native view did not reach the expected state within five seconds" },
        condition: () -> Boolean,
    ) {
        repeat(100) {
            if (condition()) return
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
        }
        assertTrue(condition(), failureMessage())
    }

    private fun UIViewController.players(): List<AVPlayerViewController> =
        childViewControllers.filterIsInstance<UIViewController>().flatMap {
            if (it is AVPlayerViewController) listOf(it) else it.players()
        }

    private fun UIView.webViews(): List<WKWebView> =
        subviews.filterIsInstance<UIView>().flatMap {
            if (it is WKWebView) listOf(it) else it.webViews()
        }

    private fun UIView.preloadHosts(): List<MediaPreloadHostView> =
        subviews.filterIsInstance<UIView>().flatMap {
            if (it is MediaPreloadHostView) listOf(it) else it.preloadHosts()
        }
}

// Local missing files keep host lifecycle coverage independent of network/decoder availability.
private const val FIRST_URL = "file:///tmp/orbin-host-test-first.mp4"
private const val SECOND_URL = "file:///tmp/orbin-host-test-second.mp4"

private fun secondsForTest(time: CValue<CMTime>): Double = CMTimeGetSeconds(time)
