package com.orbin.data.sync

import java.net.Inet4Address
import java.net.NetworkInterface

internal actual fun nearbyNetworks(): List<NearbyNetwork> =
    NetworkInterface
        .getNetworkInterfaces()
        .toList()
        .filter { it.isUp && !it.isLoopback && LOCAL_INTERFACES.any(it.name::startsWith) }
        .flatMap { it.interfaceAddresses }
        .filter { it.address is Inet4Address }
        .mapNotNull { entry ->
            entry.address.hostAddress
                ?.takeIf(
                    ::localAddress,
                )?.let { NearbyNetwork(it, entry.networkPrefixLength.toInt()) }
        }

/**
 * Wi-Fi, Ethernet and this phone's own hotspot ("ap", "swlan" on most vendors), so two phones can
 * sync on mobile data alone: one shares a hotspot and the other joins it.
 */
private val LOCAL_INTERFACES = listOf("wlan", "eth", "swlan", "ap")
