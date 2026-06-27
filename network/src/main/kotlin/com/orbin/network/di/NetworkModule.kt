package com.orbin.network.di

import com.orbin.network.DohConfig
import com.orbin.network.NetworkConfig
import com.orbin.network.NetworkConfigProvider
import com.orbin.network.interceptor.HeadersInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifies the base OkHttpClient shared by providers and image loaders. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseOkHttp

/**
 * Provides the shared networking primitives: the lenient [Json] parser, and an [OkHttpClient]
 * configured for secure defaults (HTTPS-only, modern TLS), optional DNS-over-HTTPS, the
 * user-agent interceptor and opt-in logging.
 *
 * A [NetworkConfigProvider] binding must be supplied by the app/data layer; :data provides one
 * backed by DataStore. A default fallback is provided here so the module is usable standalone
 * (and in tests) before settings exist.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun providesJson(): Json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
            explicitNulls = false
        }

    @Provides
    @Singleton
    @BaseOkHttp
    fun providesOkHttpClient(configProvider: NetworkConfigProvider): OkHttpClient {
        val config = configProvider.current()

        // Bootstrap client used only to resolve the DoH endpoint itself.
        val bootstrap =
            OkHttpClient
                .Builder()
                .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
                .build()

        return OkHttpClient
            .Builder()
            .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(HeadersInterceptor(configProvider))
            .apply {
                if (config.httpsOnly) {
                    // Reject any cleartext connection at the OkHttp level.
                    connectionSpecs(
                        listOf(
                            okhttp3.ConnectionSpec.RESTRICTED_TLS,
                            okhttp3.ConnectionSpec.MODERN_TLS,
                        ),
                    )
                }
                dnsFor(config, bootstrap)?.let { dns(it) }
                if (config.enableHttpLogging) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                    )
                }
            }.build()
    }

    private fun dnsFor(
        config: NetworkConfig,
        bootstrap: OkHttpClient,
    ): Dns? =
        when (val doh = config.dnsOverHttps) {
            DohConfig.Disabled -> null
            is DohConfig.Enabled ->
                DnsOverHttps
                    .Builder()
                    .client(bootstrap)
                    .url(doh.resolverUrl.toHttpUrl())
                    .apply {
                        if (doh.bootstrapIps.isNotEmpty()) {
                            bootstrapDnsHosts(doh.bootstrapIps.map { InetAddress.getByName(it) })
                        }
                    }.build()
        }
}
