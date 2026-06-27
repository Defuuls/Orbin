package com.orbin.media.di

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.orbin.network.di.BaseOkHttp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

private const val MEMORY_CACHE_PERCENT = 0.25

/**
 * Builds the singleton Coil [ImageLoader], reusing the app's shared [OkHttpClient] (so DoH,
 * user-agent and TLS policy apply to images too) and configuring the in-memory cache. Coil's
 * default on-disk cache is used for persistence. The app exposes this loader to Coil as the
 * process singleton.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {
    @Provides
    @Singleton
    fun providesImageLoader(
        @ApplicationContext context: Context,
        @BaseOkHttp okHttpClient: OkHttpClient,
    ): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }.memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(context, MEMORY_CACHE_PERCENT)
                    .build()
            }.crossfade(true)
            .build()
}
