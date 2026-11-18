package com.orbin.media.di

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.orbin.domain.repository.VideoCacheRepository
import com.orbin.media.video.VideoPrefixCache
import com.orbin.media.video.VideoPrefixDataSource
import com.orbin.media.video.VideoServer
import com.orbin.network.di.VideoOkHttp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Proxy
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
    ): DataSource.Factory {
        // VideoServer streams uncached media over loopback HTTP. The remote client deliberately
        // rejects cleartext and uses DoH, so it cannot also be the local server's transport.
        // Never proxy local bytes or follow a redirect out of this narrowly scoped exception.
        val loopbackClient =
            OkHttpClient
                .Builder()
                .proxy(Proxy.NO_PROXY)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        val calls =
            object : Call.Factory {
                override fun newCall(request: Request): Call {
                    val url = request.url
                    val client = if (url.scheme == "http" && url.host == "127.0.0.1") loopbackClient else okHttpClient
                    return client.newCall(request)
                }
            }
        return VideoPrefixDataSource.Factory(
            // Local/downloaded files use the platform source. Remote HTTP(S) retains all policy.
            DefaultDataSource.Factory(context, OkHttpDataSource.Factory(calls)),
            cache,
        )
    }

    @Provides
    @Singleton
    fun providesVideoCacheRepository(videoServer: VideoServer): VideoCacheRepository =
        object : VideoCacheRepository {
            override suspend fun usageBytes(): Long = videoServer.cacheSize()

            override suspend fun clear() = videoServer.clearCache()

            override suspend fun setMaxCacheBytes(bytes: Long) = videoServer.setMaxCacheBytes(bytes)
        }
}
