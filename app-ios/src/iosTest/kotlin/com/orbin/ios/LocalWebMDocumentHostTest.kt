@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.orbin.ios

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSRunLoop
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.Foundation.writeToFile
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.WebKit.WKWebView
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class LocalWebMDocumentHostTest {
    @Test
    fun ownedWebMDecodesOfflineAndCannotNavigateToAnotherLocalFile() {
        val webMCase = "LocalWebMDocumentHostTest.ownedWebMDecodesOfflineAndCannotNavigateToAnotherLocalFile"
        if (!requireWebMDecodeRuntime(webMCase)) return
        val manager = NSFileManager.defaultManager
        val support = manager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
        val directory = checkNotNull(support?.path) + "/Orbin/MediaExport/" + NSUUID().UUIDString
        assertTrue(manager.createDirectoryAtPath(directory, true, null, null))
        val source = "$directory/original.webm"
        val bytes = Base64.decode(PlaybackFixtures.WEBM)
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        assertTrue(data.writeToFile(source, atomically = true))
        val delegate = WebMPageDelegate()
        val web = WKWebView(CGRectMake(0.0, 0.0, 390.0, 400.0), webMViewConfiguration())
        web.applyWebMPolicy(delegate)
        val root = UIViewController()
        root.view.addSubview(web)
        val window = UIWindow(CGRectMake(0.0, 0.0, 390.0, 844.0))
        window.rootViewController = root
        window.makeKeyAndVisible()
        try {
            val url = checkNotNull(NSURL.fileURLWithPath(source).absoluteString)
            web.loadWebMDocument(
                "<video id='v' src='${url.escapeHtmlAttribute()}' muted autoplay loop playsinline></video>",
                url,
                delegate,
            )
            await {
                var decoded = false
                var done = false
                web.evaluateJavaScript(
                    "(function(){var v=document.getElementById('v');" +
                        "return !!v&&v.currentTime>0.05&&v.getVideoPlaybackQuality().totalVideoFrames>0})()",
                ) { result, _ ->
                    decoded = result == true
                    done = true
                }
                await { done }
                decoded
            }
            val document = delegate.localDocumentUrl
            assertEquals(document, web.URL?.absoluteString)
            web.evaluateJavaScript("window.location.href='file:///etc/passwd'", completionHandler = null)
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.25))
            assertEquals(
                document,
                web.URL?.absoluteString,
                "The staged folder permission must not permit another file navigation",
            )
            assertFailsWith<IllegalArgumentException> {
                web.loadWebMDocument("<video></video>", "file:///etc/passwd", delegate)
            }
        } finally {
            delegate.releaseLocalDocument()
            web.navigationDelegate = null
            web.UIDelegate = null
            web.tearDownMedia()
            window.hidden = true
            window.rootViewController = null
            manager.removeItemAtPath(directory, null)
        }
        verifiedWebMDecodeCase(webMCase)
    }

    private fun await(condition: () -> Boolean) {
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow() < 20.seconds) {
            if (condition()) return
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
        }
        assertTrue(condition(), "The owned local WebM must decode in WebKit without a network source")
    }
}
