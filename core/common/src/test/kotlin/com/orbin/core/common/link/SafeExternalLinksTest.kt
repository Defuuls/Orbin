package com.orbin.core.common.link

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SafeExternalLinksTest {
    @Test
    fun `https urls are kept`() {
        assertThat(SafeExternalLinks.sanitizeHttps("https://example.org/a")).isEqualTo("https://example.org/a")
    }

    @Test
    fun `http and unsafe schemes are rejected`() {
        assertThat(SafeExternalLinks.sanitizeHttps("http://example.org/a")).isNull()
        assertThat(SafeExternalLinks.sanitizeHttps("javascript:alert(1)")).isNull()
        assertThat(SafeExternalLinks.sanitizeHttps("data:text/html,hi")).isNull()
        assertThat(SafeExternalLinks.sanitizeHttps("intent://scan/#Intent;end")).isNull()
    }

    @Test
    fun `blank and hostless values are rejected`() {
        assertThat(SafeExternalLinks.sanitizeHttps("")).isNull()
        assertThat(SafeExternalLinks.sanitizeHttps("https://")).isNull()
    }
}
