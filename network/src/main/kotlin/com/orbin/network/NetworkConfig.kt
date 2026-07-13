package com.orbin.network

/**
 * Runtime networking configuration, sourced from user settings. Exposed as a provider so changes
 * (e.g. toggling DoH or editing the user-agent) take effect without recreating the OkHttp client
 * graph — the client reads the latest snapshot per call where it matters.
 */
data class NetworkConfig(
    val userAgent: String = DEFAULT_USER_AGENT,
    val dnsOverHttps: DohConfig = DohConfig.Disabled,
    /** Optional HTTP/HTTPS proxy "host:port"; null means use system defaults. */
    val proxy: ProxyConfig? = null,
    /** When true, the app refuses cleartext HTTP entirely. */
    val httpsOnly: Boolean = true,
    val connectTimeoutSeconds: Long = 15,
    val readTimeoutSeconds: Long = 30,
    val disableOcspChecking: Boolean = true,
    val enableHttpLogging: Boolean = false,
) {
    companion object {
        const val DEFAULT_USER_AGENT: String = "Orbin/1.0 (Android; +https://github.com/defuuls/orbin)"
    }
}

/** DNS-over-HTTPS configuration. Disabled by default; users opt in for privacy. */
sealed interface DohConfig {
    data object Disabled : DohConfig

    /** Use a well-known resolver, or a custom DoH endpoint URL. */
    data class Enabled(
        val resolverUrl: String,
        val bootstrapIps: List<String> = emptyList(),
    ) : DohConfig

    companion object {
        val Cloudflare =
            Enabled(
                resolverUrl = "https://cloudflare-dns.com/dns-query",
                bootstrapIps = listOf("1.1.1.1", "1.0.0.1"),
            )
        val Google =
            Enabled(
                resolverUrl = "https://dns.google/dns-query",
                bootstrapIps = listOf("8.8.8.8", "8.8.4.4"),
            )
        val OpenDns =
            Enabled(
                resolverUrl = "https://doh.opendns.com/dns-query",
                bootstrapIps = listOf("208.67.222.222", "208.67.220.220"),
            )
        val NextDns =
            Enabled(
                resolverUrl = "https://dns.nextdns.io",
                bootstrapIps = listOf("45.90.28.0", "45.90.30.0"),
            )
    }
}

data class ProxyConfig(
    val host: String,
    val port: Int,
    val type: ProxyType = ProxyType.HTTP,
)

enum class ProxyType { HTTP, SOCKS }

/** Supplies the current [NetworkConfig]. Implemented in :data over DataStore settings. */
fun interface NetworkConfigProvider {
    fun current(): NetworkConfig
}
