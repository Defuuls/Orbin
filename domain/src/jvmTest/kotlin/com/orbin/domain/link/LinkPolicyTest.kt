package com.orbin.domain.link

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class LinkPolicyTest {
    @Test
    fun `well formed https links are kept, trimmed`() {
        assertThat(LinkPolicy.sanitize("https://example.org/a")).isEqualTo("https://example.org/a")
        assertThat(LinkPolicy.sanitize(" https://example.com/path ")).isEqualTo("https://example.com/path")
        assertThat(LinkPolicy.sanitize("HTTPS://example.com")).isEqualTo("HTTPS://example.com")
        assertThat(LinkPolicy.sanitize("https://example.com/a?b=c#d")).isEqualTo("https://example.com/a?b=c#d")
    }

    @Test
    fun `everything that is not well formed https is rejected`() {
        listOf(
            "http://example.com",
            "https://",
            "https:///path",
            "https://example.com/a b",
            "https://example.com\\@evil.test",
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "data:text/html,hi",
            "vbscript:msgbox",
            "file:///tmp/file",
            "about:blank",
            "blob:https://example.com/id",
            "intent://scan/#Intent;end",
            "market://details?id=x",
            "mailto:someone@example.com",
            "www.example.com",
            "",
            "   ",
        ).forEach { assertWithMessage(it).that(LinkPolicy.sanitize(it)).isNull() }
    }

    @Test
    fun `links open in app only when the setting is on and the platform has the browser`() {
        val url = "https://example.org/a"

        assertThat(LinkPolicy.route(url, openInApp = true, inAppAvailable = true)).isEqualTo(LinkRoute.InApp(url))
        assertThat(LinkPolicy.route(url, openInApp = false, inAppAvailable = true)).isEqualTo(LinkRoute.External(url))
        assertThat(LinkPolicy.route(url, openInApp = true, inAppAvailable = false)).isEqualTo(LinkRoute.External(url))
        assertThat(LinkPolicy.route(url, openInApp = false, inAppAvailable = false)).isEqualTo(LinkRoute.External(url))
    }

    @Test
    fun `routes carry the sanitized link, and unsafe links are rejected whatever the setting`() {
        assertThat(LinkPolicy.route(" https://example.org ", openInApp = true, inAppAvailable = true))
            .isEqualTo(LinkRoute.InApp("https://example.org"))
        for (openInApp in listOf(true, false)) {
            assertThat(LinkPolicy.route("javascript:alert(1)", openInApp, inAppAvailable = true))
                .isEqualTo(LinkRoute.Rejected)
            assertThat(LinkPolicy.route("http://example.org", openInApp, inAppAvailable = true))
                .isEqualTo(LinkRoute.Rejected)
        }
    }

    @Test
    fun `the in-app browser loads only pages a tapped link could open`() {
        assertThat(LinkPolicy.allowsInAppPage("https://example.org/next")).isTrue()
        assertThat(LinkPolicy.allowsInAppPage("http://example.org/next")).isFalse()
        assertThat(LinkPolicy.allowsInAppPage("intent://scan/#Intent;end")).isFalse()
        assertThat(LinkPolicy.allowsInAppPage("file:///data/data/com.orbin/x")).isFalse()
    }
}
