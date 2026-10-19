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
internal actual fun nearbyAddresses(): List<String> =
    memScoped {
        val head = alloc<CPointerVar<ifaddrs>>()
        if (getifaddrs(head.ptr) != 0) return@memScoped emptyList()
        val addresses = mutableListOf<Pair<String, String>>()
        try {
            var item = head.value
            while (item != null) {
                val entry = item.pointed
                val address = entry.ifa_addr
                if (address?.pointed?.sa_family?.toInt() == AF_INET) {
                    val ip =
                        inet_ntoa(
                            address
                                .reinterpret<sockaddr_in>()
                                .pointed.sin_addr
                                .readValue(),
                        )?.toKString()
                    if (ip != null && localAddress(ip) && !ip.startsWith("127.")) {
                        addresses += entry.ifa_name?.toKString().orEmpty() to ip
                    }
                }
                item = entry.ifa_next
            }
        } finally {
            freeifaddrs(head.value)
        }
        addresses.sortedBy { if (it.first == "en0") 0 else 1 }.map { it.second }
    }
