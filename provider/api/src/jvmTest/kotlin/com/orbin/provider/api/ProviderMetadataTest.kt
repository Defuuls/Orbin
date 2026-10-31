package com.orbin.provider.api

import com.orbin.core.model.ProviderId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderMetadataTest {
    private val metadata =
        ProviderMetadata(
            id = ProviderId("p"),
            displayName = "P",
            baseUrl = "https://boards.example.org",
            mediaHosts = setOf("i.example-cdn.org"),
        )

    @Test
    fun ownMediaUrlsAreHttpsOnTheSiteOrAMediaHost() {
        assertTrue(metadata.isOwnMediaUrl("https://boards.example.org/g/1s.jpg"))
        assertTrue(metadata.isOwnMediaUrl("https://I.EXAMPLE-CDN.ORG/g/1s.jpg"))
        assertFalse(metadata.isOwnMediaUrl("http://i.example-cdn.org/g/1s.jpg"))
        assertFalse(metadata.isOwnMediaUrl("https://tracker.example/u.png"))
        assertFalse(metadata.isOwnMediaUrl("https://i.example-cdn.org.evil.test/x.png"))
        assertFalse(metadata.isOwnMediaUrl("https://user@evil.test/x.png"))
        assertFalse(metadata.isOwnMediaUrl("javascript:alert(1)"))
        assertFalse(metadata.isOwnMediaUrl("not a url"))
    }
}
