package com.orbin.network.dns

import com.google.common.truth.Truth.assertThat
import com.orbin.network.DohConfig
import com.orbin.network.DohFallbackTracker
import com.orbin.network.NetworkConfig
import okhttp3.Dns
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Encrypted DNS is not user-defeatable, which puts real weight on the fallback: it is the only
 * thing standing between a DoH-blocking network and an app that cannot resolve anything. These
 * tests pin both halves of that — that it engages when the resolver is blocked, and that it does
 * *not* misreport a merely nonexistent hostname as interference.
 */
class DynamicDnsTest {
    private val tracker = DohFallbackTracker()
    private val resolved = listOf(InetAddress.getByName("93.184.216.34"))

    @Test
    fun anEncryptedLookupIsUsedAndReportedAsPrivate() {
        val dns = dns(encrypted = working(), system = failing())

        assertThat(dns.lookup("example.com")).isEqualTo(resolved)
        assertThat(tracker.usingSystemFallback.value).isFalse()
    }

    @Test
    fun aBlockedResolverFallsBackToSystemDnsAndIsReported() {
        val dns = dns(encrypted = failing(), system = working())

        assertThat(dns.lookup("example.com")).isEqualTo(resolved)
        assertThat(tracker.usingSystemFallback.value).isTrue()
    }

    /**
     * A hostname that does not exist fails over DoH exactly as a blocked resolver does. Reporting
     * that as a privacy downgrade would fire the warning on every dead link, so the system resolver
     * has to actually succeed before the fallback counts.
     */
    @Test
    fun anUnresolvableHostnameIsNotMistakenForABlockedResolver() {
        val dns = dns(encrypted = failing(), system = failing())

        try {
            dns.lookup("no-such-host.invalid")
            error("expected UnknownHostException")
        } catch (e: UnknownHostException) {
            assertThat(e).hasMessageThat().contains("doh")
        }
        assertThat(tracker.usingSystemFallback.value).isFalse()
    }

    /** Moving back onto a network that permits DoH must clear the warning without a restart. */
    @Test
    fun anEncryptedLookupClearsAnEarlierFallback() {
        var encrypted: Dns = failing()
        val dns = dns(encrypted = { hostname -> encrypted.lookup(hostname) }, system = working())

        dns.lookup("example.com")
        assertThat(tracker.usingSystemFallback.value).isTrue()

        encrypted = working()
        dns.lookup("example.com")
        assertThat(tracker.usingSystemFallback.value).isFalse()
    }

    /** Each resolver is built once and reused; rebuilding per lookup would drop OkHttp's DoH cache. */
    @Test
    fun theEncryptedResolverIsBuiltOncePerConfiguration() {
        var built = 0
        val dns =
            DynamicDns(
                configProvider = { NetworkConfig(dnsOverHttps = DohConfig.Cloudflare) },
                fallbackTracker = tracker,
                systemDns = failing(),
                encryptedDnsFactory = {
                    built++
                    working()
                },
            )

        repeat(3) { dns.lookup("example.com") }

        assertThat(built).isEqualTo(1)
    }

    private fun dns(
        encrypted: Dns,
        system: Dns,
    ) = DynamicDns(
        configProvider = { NetworkConfig(dnsOverHttps = DohConfig.Cloudflare) },
        fallbackTracker = tracker,
        systemDns = system,
        encryptedDnsFactory = { encrypted },
    )

    private fun working() = Dns { resolved }

    private fun failing() = Dns { throw UnknownHostException("doh: blocked") }
}
