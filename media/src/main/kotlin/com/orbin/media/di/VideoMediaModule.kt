package com.orbin.media.di

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.orbin.network.di.VideoOkHttp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VideoMediaDataSource

@Module
@InstallIn(SingletonComponent::class)
object VideoMediaModule {
    @Provides
    @Singleton
    fun providesVideoCache(
        @ApplicationContext context: Context,
    ): Cache =
        SimpleCache(
            File(context.cacheDir, VIDEO_CACHE_DIR),
            LeastRecentlyUsedCacheEvictor(VIDEO_CACHE_MAX_BYTES),
            StandaloneDatabaseProvider(context),
        )

    @Provides
    @Singleton
    @VideoMediaDataSource
    fun providesVideoDataSourceFactory(
        cache: Cache,
        @VideoOkHttp okHttpClient: OkHttpClient,
    ): DataSource.Factory {
        val upstream = OkHttpDataSource.Factory(okHttpClient)
        return CacheDataSource
            .Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private const val VIDEO_CACHE_DIR = "media3-video-cache"
    private const val VIDEO_CACHE_MAX_BYTES = 128L * 1024L * 1024L
}
