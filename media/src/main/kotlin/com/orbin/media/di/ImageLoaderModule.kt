package com.orbin.media.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.orbin.core.model.AppSettings
import com.orbin.domain.repository.SettingsRepository
import com.orbin.media.ImagePreloader
import com.orbin.network.di.BaseOkHttp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import javax.inject.Singleton

private const val MEMORY_CACHE_PERCENT = 0.25
private const val BYTES_PER_MB = 1024L * 1024L

/**
 * Builds the singleton Coil [ImageLoader], reusing the app's shared [OkHttpClient] (so DoH,
 * user-agent and TLS policy apply to images too) and configuring the in-memory and on-disk
 * caches. The disk cache is explicitly bounded by [AppSettings.imageCacheLimitMb] instead of
 * Coil's free-space-relative default, so heavy media browsing can't grow it unbounded. The app
 * exposes this loader to Coil as the process singleton.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {
    @Provides
    @Singleton
    fun providesImageLoader(
        @ApplicationContext context: Context,
        @BaseOkHttp okHttpClient: OkHttpClient,
        settingsRepository: SettingsRepository,
    ): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                // Animate GIFs (and animated WebP) instead of showing a static first frame.
                add(AnimatedImageDecoder.Factory())
            }.memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(context, MEMORY_CACHE_PERCENT)
                    .build()
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    // Coil resolves this lazily, off the main thread, on first disk-cache use,
                    // so reading the persisted value here does not block startup. The size is
                    // fixed for the process lifetime — a change applies on next launch.
                    .maxSizeBytes(runBlocking { settingsRepository.settings.first() }.imageCacheLimitMb * BYTES_PER_MB)
                    .build()
            }.crossfade(true)
            .build()

    @Provides
    @Singleton
    fun providesImagePreloader(
        @ApplicationContext context: Context,
        imageLoader: ImageLoader,
    ): ImagePreloader = ImagePreloader(context, imageLoader)
}
