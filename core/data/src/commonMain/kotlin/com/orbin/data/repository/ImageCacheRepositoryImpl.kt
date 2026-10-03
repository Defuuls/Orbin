package com.orbin.data.repository

import com.orbin.domain.repository.ImageCacheRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

class ImageCacheRepositoryImpl(
    private val cacheDirectories: List<Path>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val fileSystem: FileSystem = com.orbin.data.util.defaultFileSystem,
) : ImageCacheRepository {
    override suspend fun usageBytes(): Long =
        withContext(ioDispatcher) {
            cacheDirectories.sumOf { totalSize(it) }
        }

    override suspend fun clear() =
        withContext(ioDispatcher) {
            cacheDirectories.forEach { dir ->
                fileSystem.listOrNull(dir)?.forEach { file ->
                    deleteRecursively(file)
                }
            }
        }

    private fun totalSize(path: Path): Long {
        val metadata = fileSystem.metadataOrNull(path) ?: return 0L
        if (metadata.isRegularFile) return metadata.size ?: 0L
        if (metadata.isDirectory) {
            return fileSystem.listOrNull(path)?.sumOf { totalSize(it) } ?: 0L
        }
        return 0L
    }

    private fun deleteRecursively(path: Path) {
        val metadata = fileSystem.metadataOrNull(path) ?: return
        if (metadata.isDirectory) {
            fileSystem.listOrNull(path)?.forEach { deleteRecursively(it) }
        }
        runCatching { fileSystem.delete(path) }
    }
}
