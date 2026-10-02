package com.orbin.network.policy

import com.google.common.truth.Truth.assertThat
import com.orbin.network.NetworkConfig
import org.junit.Test

class RequestPolicyTest {
    private val config = NetworkConfig(userAgent = "OrbinTest")

    @Test
    fun `api gets may use a short lived cache`() {
        val edits = RequestPolicy.headers(config, "GET", "/b/catalog.json", "https://a.example.org/")
        assertThat(edits.set).containsEntry("User-Agent", "OrbinTest")
        assertThat(edits.set).containsEntry("Cache-Control", "max-age=60")
        assertThat(edits.remove).contains("Pragma")
    }

    @Test
    fun `mutations are never cached`() {
        val edits = RequestPolicy.headers(config, "POST", "/post", "https://a.example.org/")
        assertThat(edits.set).containsEntry("Cache-Control", "no-store")
        assertThat(edits.set).containsEntry("Pragma", "no-cache")
    }

    @Test
    fun `static media gets a media accept and its own origin as referer`() {
        val edits = RequestPolicy.headers(config, "GET", "/g/1690000000000.JPG", "https://i.example.org/")
        assertThat(edits.set["Accept"]).contains("image/*")
        assertThat(edits.set).containsEntry("Referer", "https://i.example.org/")
        assertThat(edits.remove).containsAtLeast("Cache-Control", "Pragma")
    }

    @Test
    fun `https only refuses cleartext and only cleartext`() {
        assertThat(RequestPolicy.allows(config, "https")).isTrue()
        assertThat(RequestPolicy.allows(config, "http")).isFalse()
        assertThat(RequestPolicy.allows(config.copy(httpsOnly = false), "http")).isTrue()
    }
}
