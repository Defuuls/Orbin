package com.orbin.core.common.web

import android.webkit.CookieManager
import android.webkit.WebView
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35])
class WebViewBrowsingDataTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `clearing removes the cookies a search left behind`() {
        val cookies = CookieManager.getInstance()
        cookies.setCookie("https://find.4chan.org/", "cf_clearance=verification-token")
        assertThat(cookies.hasCookies()).isTrue()

        WebViewBrowsingData.clear()

        assertThat(cookies.hasCookies()).isFalse()
        assertThat(cookies.getCookie("https://find.4chan.org/")).isNull()
    }

    @Test
    fun `clearing through the closing view also drops its cache history and form data`() {
        val view = WebView(context)
        CookieManager.getInstance().setCookie("https://find.4chan.org/", "session=1")

        WebViewBrowsingData.clear(view)

        val shadow = shadowOf(view)
        assertThat(shadow.wasClearCacheCalled()).isTrue()
        assertThat(shadow.didClearCacheIncludeDiskFiles()).isTrue()
        assertThat(shadow.wasClearHistoryCalled()).isTrue()
        assertThat(shadow.wasClearFormDataCalled()).isTrue()
        assertThat(CookieManager.getInstance().hasCookies()).isFalse()
    }

    @Test
    fun `startup deletes what a killed process left on disk and nothing else`() {
        val stored = WebViewBrowsingData.storedDirectories(context)
        stored.forEach { directory ->
            File(directory, "Default").mkdirs()
            File(directory, "Default/Cookies").writeText("cf_clearance=verification-token")
        }
        val unrelated = File(context.cacheDir, "unrelated").apply { writeText("keep") }

        assertThat(WebViewBrowsingData.deleteStoredFiles(context)).isTrue()

        stored.forEach { assertThat(it.exists()).isFalse() }
        assertThat(unrelated.exists()).isTrue()
        unrelated.delete()
    }

    @Test
    fun `startup cleanup succeeds when there is nothing to delete`() {
        WebViewBrowsingData.storedDirectories(context).forEach { it.deleteRecursively() }

        assertThat(WebViewBrowsingData.deleteStoredFiles(context)).isTrue()
        // Looking must not create the directories it would then have to delete next launch.
        WebViewBrowsingData.storedDirectories(context).forEach { assertThat(it.exists()).isFalse() }
    }

    @Test
    fun `the directories are WebView's own inside the app's private storage`() {
        val names = WebViewBrowsingData.storedDirectories(context).map { it.relativeTo(context.dataDir).path }

        assertThat(names).containsExactly("app_webview", "cache/WebView")
    }
}
