package com.orbin.data.sync

import java.net.Inet4Address
import java.net.NetworkInterface

internal actual fun nearbyNetworks(): List<NearbyNetwork> =
    NetworkInterface
        .getNetworkInterfaces()
        .toList()
        .filter { it.isUp && !it.isLoopback && (it.name.startsWith("wlan") || it.name.startsWith("eth")) }
        .flatMap { it.interfaceAddresses }
        .filter { it.address is Inet4Address }
        .mapNotNull { entry ->
            entry.address.hostAddress
                ?.takeIf(
                    ::localAddress,
                )?.let { NearbyNetwork(it, entry.networkPrefixLength.toInt()) }
        }
