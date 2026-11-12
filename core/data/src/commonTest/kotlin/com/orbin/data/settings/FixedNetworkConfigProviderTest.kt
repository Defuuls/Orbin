package com.orbin.data.settings

import com.orbin.core.model.DohProvider
import com.orbin.network.DohConfig
import com.orbin.network.NetworkConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FixedNetworkConfigProviderTest {
    @Test
    fun mapsEveryProviderToItsResolverAndBootstraps() {
        assertEquals(DohConfig.Cloudflare, DohProvider.CLOUDFLARE.toDohConfig())
        assertEquals(DohConfig.OpenDns, DohProvider.OPENDNS.toDohConfig())
        assertEquals(DohConfig.NextDns, DohProvider.NEXTDNS.toDohConfig())
        assertEquals(DohConfig.Google, DohProvider.GOOGLE.toDohConfig())
    }

    @Test
    fun encryptedDnsRequestCarriesStrictAsRequired() {
        val open = encryptedDnsRequest(DohProvider.OPENDNS, strict = false)
        assertEquals(DohConfig.OpenDns.resolverUrl, open.resolverUrl)
        assertEquals(DohConfig.OpenDns.bootstrapIps, open.bootstrapIps)
        assertFalse(open.required)

        val strict = encryptedDnsRequest(DohProvider.GOOGLE, strict = true)
        assertEquals(DohConfig.Google.resolverUrl, strict.resolverUrl)
        assertTrue(strict.required)
    }

    @Test
    fun updateDohChangesOnlyTheResolverAndKeepsTheInitialUserAgent() {
        val provider =
            FixedNetworkConfigProvider(
                NetworkConfig(userAgent = "Orbin/1.0 (iOS; +https://github.com/defuuls/orbin)"),
            )

        provider.updateDoh(DohProvider.NEXTDNS, strict = true)

        val current = provider.current()
        assertEquals("Orbin/1.0 (iOS; +https://github.com/defuuls/orbin)", current.userAgent)
        assertEquals(DohConfig.NextDns, current.dnsOverHttps)
        assertTrue(current.strictDoh)
        assertTrue(current.httpsOnly)
    }
}
