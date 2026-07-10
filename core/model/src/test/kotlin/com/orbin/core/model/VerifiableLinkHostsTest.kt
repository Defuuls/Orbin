package com.orbin.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VerifiableLinkHostsTest {
    @Test
    fun `supported hosts match with and without subdomains`() {
        assertThat(VerifiableLinkHosts.isSupported("https://gofile.io/d/Ab12Cd")).isTrue()
        assertThat(VerifiableLinkHosts.isSupported("https://www.gofile.io/d/Ab12Cd")).isTrue()
        assertThat(VerifiableLinkHosts.isSupported("https://fast-file.ru/abc/file.zip")).isTrue()
        assertThat(VerifiableLinkHosts.isSupported("https://mega.nz/file/abc#key")).isTrue()
        assertThat(VerifiableLinkHosts.isSupported("http://mega.co.nz/#!abc!key")).isTrue()
    }

    @Test
    fun `other hosts and lookalikes do not match`() {
        assertThat(VerifiableLinkHosts.isSupported("https://example.com/file")).isFalse()
        assertThat(VerifiableLinkHosts.isSupported("https://notgofile.io/d/x")).isFalse()
        assertThat(VerifiableLinkHosts.isSupported("https://gofile.io.evil.com/d/x")).isFalse()
        assertThat(VerifiableLinkHosts.isSupported("https://omega.nz/file/x")).isFalse()
    }

    @Test
    fun `ports and blank input are handled`() {
        assertThat(VerifiableLinkHosts.isSupported("https://gofile.io:443/d/Ab12Cd")).isTrue()
        assertThat(VerifiableLinkHosts.isSupported("")).isFalse()
    }
}
