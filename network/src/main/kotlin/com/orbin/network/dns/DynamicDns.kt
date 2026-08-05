package com.orbin.network.dns

import com.orbin.network.DohConfig
import com.orbin.network.DohFallbackTracker
import com.orbin.network.NetworkConfigProvider
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves through the configured DoH resolver, falling back to the system resolver only when that
 * resolver is unreachable.
 *
 * Encrypted DNS has no off switch, so this is the one place the guarantee can bend — and it bends
 * narrowly. A DoH failure on its own is not evidence of a blocked resolver: a hostname that does
 * not exist fails over DoH too, and treating that as interference would light up the warning on
 * every dead link. Only a failure the *system* resolver can answer means something is blocking the
 * resolver. When both fail, the original DoH error is rethrown untouched.
 */
internal class DynamicDns(
    private val configProvider: NetworkConfigProvider,
    private val fallbackTracker: DohFallbackTracker,
    private val systemDns: Dns = Dns.SYSTEM,
    private val encryptedDnsFactory: (DohConfig) -> Dns,
) : Dns {
    private val encryptedDnsCache = ConcurrentHashMap<DohConfig, Dns>()

    @Throws(UnknownHostException::class)
    override fun lookup(hostname: String): List<InetAddress> {
        val doh = configProvider.current().dnsOverHttps
        return try {
            encryptedDnsCache
                .getOrPut(doh) { encryptedDnsFactory(doh) }
                .lookup(hostname)
                .also { fallbackTracker.recordEncrypted() }
        } catch (e: UnknownHostException) {
            val viaSystem =
                try {
                    systemDns.lookup(hostname)
                } catch (_: UnknownHostException) {
                    // Unresolvable either way, so the resolver is not being blocked.
                    throw e
                }
            fallbackTracker.recordFallback()
            viaSystem
        }
    }

    companion object {
        /** Builds the real DoH resolver; [bootstrap] resolves the resolver's own hostname. */
        fun encryptedDnsFactory(bootstrap: OkHttpClient): (DohConfig) -> Dns =
            { config ->
                DnsOverHttps
                    .Builder()
                    .client(bootstrap)
                    .url(config.resolverUrl.toHttpUrl())
                    .apply {
                        if (config.bootstrapIps.isNotEmpty()) {
                            bootstrapDnsHosts(config.bootstrapIps.map { InetAddress.getByName(it) })
                        }
                    }.build()
            }
    }
}
