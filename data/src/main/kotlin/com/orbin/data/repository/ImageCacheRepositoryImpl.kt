package com.orbin.data.repository

import android.content.Context
import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.domain.repository.ImageCacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCacheRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @Dispatcher(OrbinDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    ) : ImageCacheRepository {
        private val directory = context.cacheDir.resolve(IMAGE_CACHE_DIRECTORY)

        override suspend fun usageBytes(): Long =
            withContext(ioDispatcher) {
                directory.totalSize()
            }

        override suspend fun clear() =
            withContext(ioDispatcher) {
                directory.listFiles()?.forEach(File::deleteRecursively)
                Unit
            }

        private fun File.totalSize(): Long =
            when {
                !exists() -> 0L
                isFile -> length()
                else -> listFiles()?.sumOf { it.totalSize() } ?: 0L
            }

        private companion object {
            const val IMAGE_CACHE_DIRECTORY = "image_cache"
        }
    }
