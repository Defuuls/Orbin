package com.orbin.data.settings

import com.orbin.core.model.DohProvider
import com.orbin.network.DohConfig
import com.orbin.network.NetworkConfig
import com.orbin.network.NetworkConfigProvider
import kotlin.concurrent.Volatile

/**
 * Supplies the network configuration: HTTPS only, customizable DNS over HTTPS resolver,
 * default user agent and default timeouts.
 *
 * Kept apart from [SettingsRepositoryImpl] on purpose: the HTTP client is built from this, and the
 * settings stores come from the shared graph, which is built from the HTTP client.
 */
class FixedNetworkConfigProvider : NetworkConfigProvider {
    @Volatile
    private var config = NetworkConfig()

    override fun current(): NetworkConfig = config

    fun updateDoh(
        provider: DohProvider,
        strict: Boolean = false,
    ) {
        val doh =
            when (provider) {
                DohProvider.CLOUDFLARE -> DohConfig.Cloudflare
                DohProvider.OPENDNS -> DohConfig.OpenDns
                DohProvider.NEXTDNS -> DohConfig.NextDns
                DohProvider.GOOGLE -> DohConfig.Google
            }
        config =
            config.copy(
                dnsOverHttps = doh,
                strictDoh = strict,
            )
    }
}
