package com.orbin.media.di

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.orbin.network.di.VideoOkHttp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
        @VideoOkHttp okHttpClient: OkHttpClient,
    ): DataSource.Factory = OkHttpDataSource.Factory(okHttpClient)
}
