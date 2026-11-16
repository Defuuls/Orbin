package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.DohProvider
import com.orbin.network.DohConfig
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemMediaPolicyTest {
    @AfterTest
    fun restore() {
        SystemMediaPolicy.apply(AppSettings.Default)
    }

    /** Passing "not required" makes iOS use the system resolver and ignore the DoH one entirely. */
    @Test
    fun iosAlwaysRequiresEncryptedResolutionForEveryResolver() {
        DohProvider.entries.forEach { provider ->
            val request = iosEncryptedDnsRequest(provider)
            assertTrue(request.required, "$provider must require encrypted DNS")
            assertTrue(request.resolverUrl.startsWith("https://"))
            assertTrue(request.bootstrapIps.isNotEmpty(), "$provider must be reachable without a lookup")
        }
        assertEquals(DohConfig.Cloudflare.resolverUrl, iosEncryptedDnsRequest(DohProvider.CLOUDFLARE).resolverUrl)
    }

    @Test
    fun remoteMediaIsOnlyHandedToTheSystemWhenAllowed() {
        listOf("https://i.example/a.mp4", "http://i.example/a.mp4", "HTTPS://I.EXAMPLE/A.WEBM").forEach { url ->
            assertTrue(permitsSystemLoad(url, systemNetworkAllowed = true))
            assertFalse(permitsSystemLoad(url, systemNetworkAllowed = false), url)
        }
    }

    @Test
    fun aSavedFileAlwaysPlays() {
        listOf("file:///var/mobile/Orbin/MediaExport/x/a.webm", "FILE:///a.mp4", "  file:///a.mp4").forEach { url ->
            assertTrue(permitsSystemLoad(url, systemNetworkAllowed = false), url)
        }
    }

    /** Anything that is not plainly a local file is treated as something that needs the network. */
    @Test
    fun anUnrecognisedSchemeIsTreatedAsRemote() {
        listOf("", "a.mp4", "/var/a.mp4", "data:video/mp4;base64,AA", "blob:x", "ftp://h/a.mp4", "filez://a").forEach {
            assertFalse(permitsSystemLoad(it, systemNetworkAllowed = false), it)
        }
    }

    @Test
    fun thePolicyFollowsTheStrictSetting() {
        SystemMediaPolicy.apply(AppSettings.Default.copy(strictDohEnabled = true))
        assertFalse(SystemMediaPolicy.streamingAllowed.value)
        assertFalse(SystemMediaPolicy.permits("https://i.example/a.mp4"))
        assertTrue(SystemMediaPolicy.permits("file:///a.mp4"))

        SystemMediaPolicy.apply(AppSettings.Default.copy(strictDohEnabled = false))
        assertTrue(SystemMediaPolicy.streamingAllowed.value)
        assertTrue(SystemMediaPolicy.permits("https://i.example/a.mp4"))
    }

    @Test
    fun theSettingSaysWhatItTurnsOffOnIos() {
        assertTrue("streaming" in STRICT_DNS_HINT && "search" in STRICT_DNS_HINT)
        assertTrue("Save the file" in STREAMING_BLOCKED_MESSAGE)
    }
}
