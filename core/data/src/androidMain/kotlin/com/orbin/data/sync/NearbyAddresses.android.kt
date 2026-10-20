package com.orbin.data.sync

import java.net.Inet4Address
import java.net.NetworkInterface

internal actual fun nearbyAddresses(): List<String> =
    NetworkInterface
        .getNetworkInterfaces()
        .toList()
        .filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") }
        .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .mapNotNull { it.hostAddress }
        .filter(::localAddress)
