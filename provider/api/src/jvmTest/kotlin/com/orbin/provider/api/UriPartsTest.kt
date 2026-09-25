package com.orbin.provider.api

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.net.URI

/**
 * Differential test: [UriParts] must agree with `java.net.URI` on every input here, so the
 * sanitizers built on it accept and reject exactly what they did on the JVM.
 */
class UriPartsTest {
    private val corpus =
        listOf(
            // Ordinary absolute URLs.
            "https://example.test/x.jpg",
            "HTTPS://Example.Test/x.jpg",
            "http://example.test",
            "https://example.test:8443/a/b.jpg?w=1#top",
            "https://user:pw@example.test/x.jpg",
            "https://sub.domain.example.test./x",
            "https://192.0.2.1/x.jpg",
            "https://256.0.2.1/x.jpg",
            "https://1.2.3/x.jpg",
            "https://[2001:db8::1]/x.jpg",
            "https://[2001:db8::1]:443/x.jpg",
            "https://[::ffff:192.0.2.1]/x",
            // Hosts java.net.URI refuses to call a host.
            "https://my_host/x.jpg",
            "https://a.1/x.jpg",
            "https://-bad.test/x",
            "https://bad-.test/x",
            "https://example.test:port/x",
            "https:///x.jpg",
            "https://",
            "https:",
            // Relative references.
            "/relative/path.jpg",
            "//cdn.example.test/x.jpg",
            "relative.jpg",
            "./a/../b.jpg",
            "?q=1",
            "#frag",
            "",
            // Opaque and dangerous schemes.
            "mailto:someone@example.test",
            "javascript:alert(1)",
            "data:text/html,hi",
            "ftp://example.test/x",
            "tel:+15551234",
            // Characters java.net.URI rejects.
            "https://example.test/a b.jpg",
            "https://example.test/a\"b",
            "https://example.test/a<b>",
            "https://example.test/a\\b",
            "https://example.test/a^b",
            "https://example.test/a`b",
            "https://example.test/a{b}",
            "https://example.test/a|b",
            "https://example.test/a#b#c",
            "https://example.test/%zz",
            "https://example.test/%4",
            "https://example.test/a[b]",
            "1abc:def",
            "a b:c",
            // Escapes and non-ASCII.
            "https://example.test/a%20b.jpg",
            "https://example.test/%E2%9C%93.png",
            "https://example.test/café.jpg",
            "/files/%C3%A9t%C3%A9.webm",
            // Adversarial shapes.
            "HTTPS://EXAMPLE.TEST:/x",
            "https://user@[::1]/x",
            "https://example.test?x",
            "https://example.test#f",
            "https:/x",
            "https:x",
            "javascript://example.test/%0Aalert(1)",
            "jav%61script:alert(1)",
            "vbscript:msgbox(1)",
            "//",
            "///x",
            "https://ex%61mple.test/x",
            "https://例え.jp/x",
            "https://example.test/../x",
            "https://example..test/x",
            "https://.example.test/x",
            "https://example.test:80:90/x",
            "https://@example.test/x",
            "https://a@b@example.test/x",
            "https://[::1/x",
            "https://[zz::1]/x",
            "https://example.test/%00",
            "https://example.test/%FF",
        )

    @Test
    fun `agrees with java net URI on acceptance, scheme, host and path`() {
        val mismatches =
            corpus.mapNotNull { input ->
                val expected = runCatching { URI(input) }.getOrNull()
                val actual = UriParts.parse(input)
                val reference = expected?.let { UriParts(it.scheme, it.host, it.path) }
                "'$input': URI=$reference, UriParts=$actual".takeIf { reference != actual }
            }
        assertWithMessage(mismatches.joinToString("\n")).that(mismatches).isEmpty()
    }
}
