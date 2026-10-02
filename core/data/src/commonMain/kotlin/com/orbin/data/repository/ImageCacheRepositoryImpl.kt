package com.orbin.data.repository

import android.content.Context
import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.domain.repository.ImageCacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import me.tatarka.inject.annotations.Inject
import com.orbin.graph.AppScope

@AppScope
class ImageCacheRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @Dispatcher(OrbinDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    ) : ImageCacheRepository {
        private val cacheDirectories =
            listOf(
                context.cacheDir.resolve(IMAGE_CACHE_DIRECTORY),
                context.cacheDir.resolve(HTTP_CACHE_DIRECTORY),
                context.cacheDir.resolve(VIDEO_CACHE_DIRECTORY),
                context.cacheDir.resolve(CLIPBOARD_CACHE_DIRECTORY),
            )

        override suspend fun usageBytes(): Long =
            withContext(ioDispatcher) {
                cacheDirectories.sumOf { it.totalSize() }
            }

        override suspend fun clear() =
            withContext(ioDispatcher) {
                cacheDirectories.forEach { dir ->
                    dir.listFiles()?.forEach(File::deleteRecursively)
                }
            }

        private fun File.totalSize(): Long =
            when {
                !exists() -> 0L
                isFile -> length()
                else -> listFiles()?.sumOf { it.totalSize() } ?: 0L
            }

        private companion object {
            const val IMAGE_CACHE_DIRECTORY = "image_cache"
            const val HTTP_CACHE_DIRECTORY = "http-cache"
            const val VIDEO_CACHE_DIRECTORY = "media3-video-cache"
            const val CLIPBOARD_CACHE_DIRECTORY = "clipboard_images"
        }
    }
