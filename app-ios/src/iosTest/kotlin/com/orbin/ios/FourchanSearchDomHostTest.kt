@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.orbin.ios

import com.orbin.core.model.ProviderId
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.api.fourchanSearchInspectionScript
import com.orbin.provider.api.parseFourchanWebSearchPage
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSURL
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Actual WebKit DOM/JavaScript over a local fixture; no live search availability assumption. */
class FourchanSearchDomHostTest {
    @Test
    fun webKitExtractsReplyIdentityAndPagination() {
        var payload: String? = null
        val delegate =
            object : NSObject(), WKNavigationDelegateProtocol {
                override fun webView(
                    webView: WKWebView,
                    didFinishNavigation: WKNavigation?,
                ) {
                    webView.evaluateJavaScript(fourchanSearchInspectionScript(0)) { value, _ ->
                        payload =
                            value as? String
                    }
                }
            }
        val configuration =
            WKWebViewConfiguration().apply {
                websiteDataStore =
                    WKWebsiteDataStore.nonPersistentDataStore()
            }
        val browser =
            WKWebView(
                CGRectMake(0.0, 0.0, 390.0, 844.0),
                configuration,
            ).apply { navigationDelegate = delegate }
        try {
            browser.loadHTMLString(
                """<html><body><input name="q"><div class="postContainer" id="pc456"><div class="post" id="p456">
                <span class="subject">Result title</span><span class="name">Anonymous</span>
                <a href="https://boards.4chan.org/g/thread/123#p456">No.456</a>
                <blockquote class="postMessage">A matching reply</blockquote></div></div>
                <a href="https://find.4chan.org/?q=test&amp;o=25">Next</a></body></html>""",
                NSURL.URLWithString("https://find.4chan.org/?q=test&o=0"),
            )
            repeat(300) {
                if (payload == null) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
            }
            assertNotNull(payload, "WebKit must inspect the local fixture")
            val provider = ProviderMetadata(ProviderId("fourchan"), "4chan", "https://boards.4chan.org")
            val page = assertNotNull(parseFourchanWebSearchPage(payload!!, provider))
            assertEquals(25, page.nextOffset)
            assertEquals(
                123L,
                page.hits
                    .single()
                    .link.key.thread.value,
            )
            assertEquals(
                456L,
                page.hits
                    .single()
                    .link.post!!
                    .value,
            )
            assertTrue(
                page.hits
                    .single()
                    .comment
                    .contains("A matching reply"),
            )
        } finally {
            browser.stopLoading()
            browser.navigationDelegate = null
        }
    }
}
