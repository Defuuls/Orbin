package com.orbin.app

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbin.core.model.ProviderId
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.api.fourchanSearchInspectionScript
import com.orbin.provider.api.parseFourchanWebSearchPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Actual Chromium DOM and JavaScript, using local fixture HTML rather than a live service. */
@RunWith(AndroidJUnit4::class)
class FourchanSearchDomTest {
    @SuppressLint("SetJavaScriptEnabled")
    @Test
    fun nativeBrowserExtractsReplyIdentityAndPaginationFromRealDomSelectors() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val result = AtomicReference<String>()
        val finished = CountDownLatch(1)
        var browser: WebView? = null
        instrumentation.runOnMainSync {
            browser =
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    webViewClient =
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView,
                                url: String?,
                            ) {
                                view.evaluateJavascript(fourchanSearchInspectionScript(0)) {
                                    result.set(it)
                                    finished.countDown()
                                }
                            }
                        }
                    loadDataWithBaseURL("https://find.4chan.org/?q=test&o=0", FIXTURE, "text/html", "utf-8", null)
                }
        }
        try {
            assertTrue("Chromium must finish inspecting the local search fixture", finished.await(15, TimeUnit.SECONDS))
            val provider = ProviderMetadata(ProviderId("fourchan"), "4chan", "https://boards.4chan.org")
            val page = parseFourchanWebSearchPage(result.get(), provider)
            assertNotNull(page)
            assertEquals(25, page!!.nextOffset)
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
            assertEquals("Result title", page.hits.single().subject)
            assertTrue(
                page.hits
                    .single()
                    .comment
                    .contains("A matching reply"),
            )
        } finally {
            instrumentation.runOnMainSync { browser?.destroy() }
        }
    }

    private companion object {
        const val FIXTURE = """
        <html><body><input name="q" value="test">
        <div class="postContainer" id="pc456"><div class="post" id="p456">
        <span class="subject">Result title</span><span class="name">Anonymous</span>
        <a href="https://boards.4chan.org/g/thread/123#p456">No.456</a>
        <blockquote class="postMessage">A matching reply <a href="https://boards.4chan.org/x/thread/999">quoted thread</a></blockquote>
        </div></div><a href="https://find.4chan.org/?q=test&amp;o=25">Next</a></body></html>
        """
    }
}
