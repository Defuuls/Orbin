package com.orbin.data.di

import android.content.Context
import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.repository.DownloadPlatformQueue
import com.orbin.data.repository.DownloadRepositoryImpl
import com.orbin.data.repository.ImageCacheRepositoryImpl
import com.orbin.data.settings.FixedNetworkConfigProvider
import com.orbin.domain.repository.DownloadRepository
import com.orbin.domain.repository.ImageCacheRepository
import com.orbin.network.NetworkConfigProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import okio.Path.Companion.toPath
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun providesNetworkConfigProvider(): NetworkConfigProvider = FixedNetworkConfigProvider()

    @Provides
    @Singleton
    fun providesDownloadRepository(
        dao: DownloadDao,
        queue: DownloadPlatformQueue,
        @Dispatcher(OrbinDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DownloadRepository = DownloadRepositoryImpl(dao, queue, ioDispatcher)

    @Provides
    @Singleton
    fun providesImageCacheRepository(
        @ApplicationContext context: Context,
        @Dispatcher(OrbinDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): ImageCacheRepository {
        val cacheDirectories =
            listOf(
                context.cacheDir.resolve("image_cache").absolutePath.toPath(),
                context.cacheDir.resolve("http-cache").absolutePath.toPath(),
                context.cacheDir.resolve("media3-video-cache").absolutePath.toPath(),
                context.cacheDir.resolve("clipboard_images").absolutePath.toPath(),
            )
        return ImageCacheRepositoryImpl(cacheDirectories, ioDispatcher)
    }
}
