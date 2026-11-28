package com.orbin.feature.browser

import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35])
class BrowserWebViewTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `pages get scripts and storage but no file, content, mixed content or pop-ups`() {
        val settings = browserWebView(context, BrowserPage()).settings

        assertThat(settings.javaScriptEnabled).isTrue()
        assertThat(settings.domStorageEnabled).isTrue()
        assertThat(settings.allowFileAccess).isFalse()
        assertThat(settings.allowContentAccess).isFalse()
        assertThat(settings.mixedContentMode).isEqualTo(WebSettings.MIXED_CONTENT_NEVER_ALLOW)
        assertThat(settings.supportMultipleWindows()).isFalse()
        assertThat(settings.javaScriptCanOpenWindowsAutomatically).isFalse()
        // safeBrowsingEnabled is set too, but Robolectric's WebSettings does not keep that value,
        // so it cannot be read back here. It is also WebView's own default on devices.
    }

    @Test
    fun `the browser loads only https pages a tapped link could open`() {
        assertThat(blocksNavigation("https://example.org/next")).isFalse()
        listOf(
            "http://example.org",
            "intent://scan/#Intent;scheme=zxing;end",
            "market://details?id=com.example",
            "file:///data/data/com.orbin/shared_prefs/x.xml",
            "content://com.orbin.provider/x",
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "mailto:someone@example.org",
        ).forEach { assertThat(blocksNavigation(it)).isTrue() }
    }

    @Test
    fun `a page's navigation to another app or a local file is refused`() {
        val client = BrowserWebViewClient(BrowserPage())
        val view = WebView(context)

        assertThat(client.shouldOverrideUrlLoading(view, request("intent://scan/#Intent;end"))).isTrue()
        assertThat(client.shouldOverrideUrlLoading(view, request("file:///sdcard/x"))).isTrue()
        assertThat(client.shouldOverrideUrlLoading(view, request("https://example.org/b"))).isFalse()
    }

    @Test
    fun `camera, microphone and location requests are denied`() {
        val chrome = BrowserChromeClient(BrowserPage())
        var denied = false
        val request =
            object : PermissionRequest() {
                override fun getOrigin(): Uri = Uri.parse("https://example.org")

                override fun getResources(): Array<String> = arrayOf(RESOURCE_VIDEO_CAPTURE, RESOURCE_AUDIO_CAPTURE)

                override fun grant(resources: Array<String>) = error("must not grant")

                override fun deny() {
                    denied = true
                }
            }
        var location: Boolean? = null

        chrome.onPermissionRequest(request)
        chrome.onGeolocationPermissionsShowPrompt("https://example.org") { _, allow, _ -> location = allow }

        assertThat(denied).isTrue()
        assertThat(location).isFalse()
    }

    @Test
    fun `file pickers are answered with nothing and new windows are not opened`() {
        val chrome = BrowserChromeClient(BrowserPage())
        val view = WebView(context)
        var picked: Array<Uri>? = arrayOf(Uri.EMPTY)

        val handled = chrome.onShowFileChooser(view, ValueCallback { picked = it }, FakeFileChooserParams)

        assertThat(handled).isTrue()
        assertThat(picked).isNull()
        assertThat(chrome.onCreateWindow(view, false, true, android.os.Message())).isFalse()
    }

    @Test
    fun `progress is shown while loading and hidden once complete`() {
        val page = BrowserPage()

        page.progress(40)
        assertThat(page.chrome.progress).isEqualTo(0.4f)
        page.progress(100)
        assertThat(page.chrome.progress).isNull()
    }

    private fun request(url: String): WebResourceRequest =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)

            override fun isForMainFrame() = true

            override fun isRedirect() = false

            override fun hasGesture() = true

            override fun getMethod() = "GET"

            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }

    private object FakeFileChooserParams : WebChromeClient.FileChooserParams() {
        override fun getMode() = MODE_OPEN

        override fun getAcceptTypes(): Array<String> = emptyArray()

        override fun isCaptureEnabled() = false

        override fun getTitle(): CharSequence? = null

        override fun getFilenameHint(): String? = null

        override fun createIntent() = error("unused")
    }
}
