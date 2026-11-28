package com.orbin.core.common.link

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InAppBrowserTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `the launch intent targets only this app's browser and carries the reader's setup`() {
        val appearance = InAppBrowser.Appearance(dark = true, amoled = true, secure = false, closeWhenHidden = false)

        val intent = checkNotNull(InAppBrowser.openIntent(context, " https://example.org/a ", appearance))

        assertThat(intent.action).isEqualTo(InAppBrowser.ACTION_OPEN)
        assertThat(intent.`package`).isEqualTo(context.packageName)
        assertThat(intent.dataString).isEqualTo("https://example.org/a")
        assertThat(InAppBrowser.appearance(intent)).isEqualTo(appearance)
    }

    @Test
    fun `unsafe links never become a launch intent`() {
        val appearance = InAppBrowser.Appearance(dark = false, amoled = false, secure = true, closeWhenHidden = true)

        listOf("http://example.org", "javascript:alert(1)", "intent://x#Intent;end", "").forEach {
            assertThat(InAppBrowser.openIntent(context, it, appearance)).isNull()
        }
    }

    @Test
    fun `a launch without the reader's setup takes the protective defaults`() {
        val bare = android.content.Intent(InAppBrowser.ACTION_OPEN)

        val appearance = InAppBrowser.appearance(bare)

        assertThat(appearance.secure).isTrue()
        assertThat(appearance.closeWhenHidden).isTrue()
    }

    @Test
    fun `the clear request goes only to this app`() {
        val intent = InAppBrowser.clearDataIntent(context)

        assertThat(intent.action).isEqualTo(InAppBrowser.ACTION_CLEAR_DATA)
        assertThat(intent.`package`).isEqualTo(context.packageName)
    }

    @Test
    fun `only the app's browser process is recognised`() {
        assertThat(InAppBrowser.isBrowserProcess("com.orbin:browser", "com.orbin")).isTrue()
        assertThat(InAppBrowser.isBrowserProcess("com.orbin", "com.orbin")).isFalse()
        assertThat(InAppBrowser.isBrowserProcess("com.other:browser", "com.orbin")).isFalse()
        assertThat(InAppBrowser.isBrowserProcess(null, "com.orbin")).isFalse()
    }
}
