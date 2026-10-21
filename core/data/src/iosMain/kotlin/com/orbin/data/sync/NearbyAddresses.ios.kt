package com.orbin.data.sync

import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.darwin.inet_ntoa
import platform.posix.AF_INET
import platform.posix.sockaddr_in

@OptIn(ExperimentalForeignApi::class)
internal actual fun nearbyNetworks(): List<NearbyNetwork> =
    memScoped {
        val head = alloc<CPointerVar<ifaddrs>>()
        if (getifaddrs(head.ptr) != 0) return@memScoped emptyList()
        val addresses = mutableListOf<NearbyNetwork>()
        try {
            var item = head.value
            while (item != null) {
                val entry = item.pointed
                val address = entry.ifa_addr?.takeIf { (entry.ifa_flags and INTERFACE_UP) != 0u }
                val name = entry.ifa_name?.toKString().orEmpty()
                if (name.startsWith("en") && address?.pointed?.sa_family?.toInt() == AF_INET) {
                    val ip =
                        inet_ntoa(
                            address
                                .reinterpret<sockaddr_in>()
                                .pointed.sin_addr
                                .readValue(),
                        )?.toKString()
                    if (ip != null && localAddress(ip) && !ip.startsWith("127.")) {
                        val netmask = entry.ifa_netmask
                        val mask =
                            netmask
                                ?.reinterpret<sockaddr_in>()
                                ?.pointed
                                ?.sin_addr
                                ?.readValue()
                                ?.let { inet_ntoa(it)?.toKString() }
                        val prefix = mask?.let(::nearbyPrefix)
                        if (prefix != null) addresses += NearbyNetwork(ip, prefix)
                    }
                }
                item = entry.ifa_next
            }
        } finally {
            freeifaddrs(head.value)
        }
        addresses
    }

private const val INTERFACE_UP = 1u

internal actual val nearbyNeedsLocalNetworkPrompt: Boolean = true
