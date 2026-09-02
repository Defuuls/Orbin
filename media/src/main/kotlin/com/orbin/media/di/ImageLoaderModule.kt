package com.orbin.media.di

import android.app.ActivityManager
import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.orbin.core.model.AppSettings
import com.orbin.media.ImagePreloader
import com.orbin.network.di.BaseOkHttp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import javax.inject.Singleton

private const val MEMORY_CACHE_PERCENT = 0.25
private const val LOW_RAM_MEMORY_CACHE_PERCENT = 0.15
private const val BYTES_PER_MB = 1024L * 1024L

/**
 * Builds the singleton Coil [ImageLoader], reusing the app's shared [OkHttpClient] and configuring
 * bounded memory/disk caches. Low-RAM devices reserve less heap for decoded images because video
 * buffers, Compose state and SQLCipher native allocations share the same constrained process.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {
    @Provides
    @Singleton
    fun providesImageLoader(
        @ApplicationContext context: Context,
        @BaseOkHttp okHttpClient: OkHttpClient,
        cacheSettings: ImageCacheSettings,
    ): ImageLoader {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryCachePercent =
            if (activityManager.isLowRamDevice) LOW_RAM_MEMORY_CACHE_PERCENT else MEMORY_CACHE_PERCENT

        return ImageLoader
            .Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                add(AnimatedImageDecoder.Factory())
            }.memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(context, memoryCachePercent)
                    .build()
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(cacheSettings.limitMb * BYTES_PER_MB)
                    .build()
            }.crossfade(true)
            .build()
    }

    @Provides
    @Singleton
    fun providesImagePreloader(
        @ApplicationContext context: Context,
        imageLoader: ImageLoader,
    ): ImagePreloader = ImagePreloader(context, imageLoader)
}
