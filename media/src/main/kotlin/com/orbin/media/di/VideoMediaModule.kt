package com.orbin.media.di

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.orbin.media.video.VideoPrefixCache
import com.orbin.media.video.VideoPrefixDataSource
import com.orbin.network.di.VideoOkHttp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VideoMediaDataSource

@Module
@InstallIn(SingletonComponent::class)
@Suppress("UnsafeOptInUsageError")
object VideoMediaModule {
    @Provides
    @Singleton
    @VideoMediaDataSource
    fun providesVideoDataSourceFactory(
        @ApplicationContext context: Context,
        @VideoOkHttp okHttpClient: OkHttpClient,
        cache: VideoPrefixCache,
    ): DataSource.Factory =
        VideoPrefixDataSource.Factory(
            // Local/downloaded media uses the platform source; all HTTP(S) traffic still passes
            // through the app's policy-protected client rather than Media3's default transport.
            DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttpClient)),
            cache,
        )
}
