package com.orbin.network.di

import android.content.Context
import com.orbin.network.DohConfig
import com.orbin.network.NetworkConfigProvider
import com.orbin.network.interceptor.HeadersInterceptor
import com.orbin.network.interceptor.HttpsOnlyInterceptor
import com.orbin.network.interceptor.VideoRetryAfterInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifies the base OkHttpClient shared by providers and image loaders. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseOkHttp

/** Qualifies the OkHttpClient used exclusively for video/audio CDN requests. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VideoOkHttp

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

        // Always negotiate modern TLS. Cleartext is intentionally absent from the connection
        // specs so HTTPS-only remains a hard privacy boundary.
        return OkHttpClient
            .Builder()
            .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionSpecs(
                listOf(
                    okhttp3.ConnectionSpec.RESTRICTED_TLS,
                    okhttp3.ConnectionSpec.MODERN_TLS,
                ),
            ).addInterceptor(HttpsOnlyInterceptor(configProvider))
            .addInterceptor(HeadersInterceptor(configProvider))
            .apply {
                dns(DynamicDns(configProvider, bootstrap))
                if (config.enableHttpLogging) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                    )
                }
            }.build()
    }

    @Provides
    @Singleton
    @VideoOkHttp
    fun providesVideoOkHttpClient(
        @BaseOkHttp base: OkHttpClient,
        @ApplicationContext context: Context,
    ): OkHttpClient {
        val cacheDir = File(context.cacheDir, "video-http-cache")
        val cache = Cache(cacheDir, VIDEO_CACHE_BYTES)
        return base
            .newBuilder()
            .cache(cache)
            // Override the base HeadersInterceptor's no-store/pragma with video-appropriate values.
            .addInterceptor { chain ->
                val req =
                    chain
                        .request()
                        .newBuilder()
                        .removeHeader("Cache-Control")
                        .removeHeader("Pragma")
                        .header("Accept", "video/webm,video/mp4,video/*,*/*;q=0.9")
                        .header("Referer", "https://boards.4chan.org/")
                        .build()
                chain.proceed(req)
            }.addInterceptor(VideoRetryAfterInterceptor())
            .build()
    }

    private class DynamicDns(
        private val configProvider: NetworkConfigProvider,
        private val bootstrap: OkHttpClient,
    ) : Dns {
        private val dohCache = ConcurrentHashMap<DohConfig.Enabled, Dns>()

        @Throws(UnknownHostException::class)
        override fun lookup(hostname: String): List<InetAddress> =
            when (val doh = configProvider.current().dnsOverHttps) {
                DohConfig.Disabled -> Dns.SYSTEM.lookup(hostname)
                is DohConfig.Enabled -> dohCache.getOrPut(doh) { doh.toDns() }.lookup(hostname)
            }

        private fun DohConfig.Enabled.toDns(): Dns =
            DnsOverHttps
                .Builder()
                .client(bootstrap)
                .url(resolverUrl.toHttpUrl())
                .apply {
                    if (bootstrapIps.isNotEmpty()) {
                        bootstrapDnsHosts(bootstrapIps.map { InetAddress.getByName(it) })
                    }
                }.build()
    }

    private companion object {
        // 100 MB disk cache for video CDN responses.
        const val VIDEO_CACHE_BYTES = 100L * 1024 * 1024
    }
}
