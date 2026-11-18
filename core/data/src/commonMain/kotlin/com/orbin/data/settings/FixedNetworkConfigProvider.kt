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
 *
 * [initial] is the starting snapshot (Android's default user agent, or iOS's). [updateDoh] then
 * changes only the resolver and strict-mode flag; the rest of the snapshot is left alone.
 */
class FixedNetworkConfigProvider(
    initial: NetworkConfig = NetworkConfig(),
) : NetworkConfigProvider {
    @Volatile
    private var config = initial

    override fun current(): NetworkConfig = config

    fun updateDoh(
        provider: DohProvider,
        strict: Boolean = false,
    ) {
        config =
            config.copy(
                dnsOverHttps = provider.toDohConfig(),
                strictDoh = strict,
            )
    }
}

/** The DoH endpoint and bootstrap addresses that belong to a [DohProvider] setting. */
fun DohProvider.toDohConfig(): DohConfig =
    when (this) {
        DohProvider.CLOUDFLARE -> DohConfig.Cloudflare
        DohProvider.OPENDNS -> DohConfig.OpenDns
        DohProvider.NEXTDNS -> DohConfig.NextDns
        DohProvider.GOOGLE -> DohConfig.Google
    }

/**
 * A resolver choice as iOS hands it to the system: the DoH URL, its bootstrap addresses, and
 * whether encrypted resolution is required. iOS always asks for required, because the system only
 * uses the DoH resolver when it is; Android has its own resolver and does not use this.
 */
data class EncryptedDnsRequest(
    val resolverUrl: String,
    val bootstrapIps: List<String>,
    val required: Boolean,
)

fun encryptedDnsRequest(
    provider: DohProvider,
    strict: Boolean,
): EncryptedDnsRequest {
    val doh = provider.toDohConfig()
    return EncryptedDnsRequest(
        resolverUrl = doh.resolverUrl,
        bootstrapIps = doh.bootstrapIps,
        required = strict,
    )
}
