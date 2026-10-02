package com.orbin.data.settings

import com.orbin.network.NetworkConfig
import com.orbin.network.NetworkConfigProvider
import me.tatarka.inject.annotations.Inject
import com.orbin.graph.AppScope

/**
 * The network configuration, which is fixed: HTTPS only, DNS over HTTPS through Cloudflare, the
 * default user agent and the default timeouts. None of those had a row anyone could reach.
 *
 * Kept apart from [SettingsRepositoryImpl] on purpose: the HTTP client is built from this, and the
 * settings stores come from the shared graph, which is built from the HTTP client.
 */
@AppScope
class FixedNetworkConfigProvider
    @Inject
    constructor() : NetworkConfigProvider {
        override fun current(): NetworkConfig = FIXED_NETWORK_CONFIG
    }

private val FIXED_NETWORK_CONFIG = NetworkConfig()
