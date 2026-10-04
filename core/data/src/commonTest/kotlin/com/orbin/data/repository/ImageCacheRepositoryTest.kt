package com.orbin.data.repository

import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageCacheRepositoryTest {
    private val testDir: Path =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "image-cache-test-${Random.nextLong()}"

    @AfterTest
    fun tearDown() {
        runCatching {
            deleteRecursively(testDir)
        }
    }

    private fun deleteRecursively(path: Path) {
        val metadata = FileSystem.SYSTEM.metadataOrNull(path) ?: return
        if (metadata.isDirectory) {
            FileSystem.SYSTEM.listOrNull(path)?.forEach { deleteRecursively(it) }
        }
        FileSystem.SYSTEM.delete(path)
    }

    @Test
    fun usageBytesOnNonExistentDirectoriesReturnsZero() =
        runTest {
            val nonExistent = testDir / "missing"
            val repository = ImageCacheRepositoryImpl(listOf(nonExistent))

            assertEquals(0L, repository.usageBytes())
        }

    @Test
    fun usageBytesCalculatesAggregatedSizeAccurately() =
        runTest {
            val dir1 = testDir / "cache1"
            val dir2 = testDir / "cache2"
            FileSystem.SYSTEM.createDirectories(dir1)
            FileSystem.SYSTEM.createDirectories(dir2)

            val file1 = dir1 / "file1.bin"
            val file2 = dir1 / "nested" / "file2.bin"
            val file3 = dir2 / "file3.bin"

            FileSystem.SYSTEM.createDirectories(dir1 / "nested")
            FileSystem.SYSTEM.write(file1) { write(ByteArray(100)) }
            FileSystem.SYSTEM.write(file2) { write(ByteArray(250)) }
            FileSystem.SYSTEM.write(file3) { write(ByteArray(50)) }

            val repository = ImageCacheRepositoryImpl(listOf(dir1, dir2))

            assertEquals(400L, repository.usageBytes())
        }

    @Test
    fun clearRemovesCachedFilesWhileKeepingRootDirectories() =
        runTest {
            val dir1 = testDir / "cache1"
            val dir2 = testDir / "cache2"
            FileSystem.SYSTEM.createDirectories(dir1)
            FileSystem.SYSTEM.createDirectories(dir2)

            val file1 = dir1 / "file1.bin"
            val nestedDir = dir1 / "nested"
            val file2 = nestedDir / "file2.bin"
            val file3 = dir2 / "file3.bin"

            FileSystem.SYSTEM.createDirectories(nestedDir)
            FileSystem.SYSTEM.write(file1) { write(ByteArray(100)) }
            FileSystem.SYSTEM.write(file2) { write(ByteArray(200)) }
            FileSystem.SYSTEM.write(file3) { write(ByteArray(300)) }

            val repository = ImageCacheRepositoryImpl(listOf(dir1, dir2))
            assertEquals(600L, repository.usageBytes())

            repository.clear()

            assertEquals(0L, repository.usageBytes())
            assertTrue(FileSystem.SYSTEM.exists(dir1))
            assertTrue(FileSystem.SYSTEM.exists(dir2))
            assertEquals(0, FileSystem.SYSTEM.list(dir1).size)
            assertEquals(0, FileSystem.SYSTEM.list(dir2).size)
        }

    @Test
    fun clearHandlesMissingDirectoriesGracefully() =
        runTest {
            val missing = testDir / "missing"
            val repository = ImageCacheRepositoryImpl(listOf(missing))

            repository.clear()
            assertEquals(0L, repository.usageBytes())
        }
}
