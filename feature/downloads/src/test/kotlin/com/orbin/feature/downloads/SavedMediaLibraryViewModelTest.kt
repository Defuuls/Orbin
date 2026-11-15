package com.orbin.feature.downloads

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.orbin.core.model.MediaLibraryRecord
import com.orbin.core.model.MediaType
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBoardRepository
import com.orbin.core.testing.repository.FakeDownloadRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.repository.DownloadRepository
import com.orbin.domain.repository.InMemoryMediaLibraryRepository
import com.orbin.domain.repository.ManagedMediaFiles
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class SavedMediaLibraryViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun exportingReadsOwnedBytesAndReportsPartialFailureWithoutRemovingLibraryRecords() =
        runTest {
            val library = InMemoryMediaLibraryRepository()
            val first = record("one", "cat.jpg")
            val second = record("two", "clip.webm")
            library.putAll(listOf(first, second))
            val managed = LocalFiles()
            val exported = mutableListOf<String>()
            val repository =
                object : DownloadRepository by FakeDownloadRepository() {
                    override suspend fun saveFile(
                        path: String,
                        fileName: String,
                        mimeType: String,
                    ): Boolean {
                        assertEquals("owned-$fileName", File(path).readText())
                        exported += fileName
                        return fileName == "cat.jpg"
                    }
                }
            val cache = Files.createTempDirectory("orbin-library-test").toFile()
            val model = model(library, managed, repository, cache)
            val store = ViewModelStore().apply { put("library", model) }
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.records.collect() }
                model.records.first { it.size == 2 }
                model.export(listOf(first.id, second.id))
                val result = model.operation.first { it != null && !it.running }!!
                assertEquals(listOf("cat.jpg", "clip.webm"), exported)
                assertEquals(2, result.completed)
                assertTrue(result.message!!.contains("1 completed"))
                assertTrue(result.message!!.contains("1 unavailable or failed"))
                assertNotNull(library.get(first.id))
                assertNotNull(library.get(second.id))
                assertTrue(File(cache, "library-export").listFiles().orEmpty().isEmpty())
            } finally {
                store.clear()
                cache.deleteRecursively()
            }
        }

    @Test
    fun deletingContinuesAfterAFileFailureAndRetainsMetadataForTheFailedRecord() =
        runTest {
            val library = InMemoryMediaLibraryRepository()
            val first = record("one", "cat.jpg")
            val second = record("two", "clip.webm")
            library.putAll(listOf(first, second))
            val managed = LocalFiles(failKey = first.storageKey)
            val cache = Files.createTempDirectory("orbin-library-test").toFile()
            val model = model(library, managed, FakeDownloadRepository(), cache)
            val store = ViewModelStore().apply { put("library", model) }
            try {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.records.collect() }
                model.records.first { it.size == 2 }
                model.delete(listOf(first.id, second.id, second.id))
                val result = model.operation.first { it != null && !it.running }!!
                assertEquals(2, result.total)
                assertNotNull(library.get(first.id))
                assertNull(library.get(second.id))
                assertTrue(second.storageKey in managed.removed)
                assertFalse(first.storageKey in managed.removed)
            } finally {
                store.clear()
                cache.deleteRecursively()
            }
        }

    private fun model(
        library: InMemoryMediaLibraryRepository,
        files: ManagedMediaFiles,
        downloads: DownloadRepository,
        cache: File,
    ): SavedMediaLibraryViewModel {
        val context = mockk<Context>()
        every { context.cacheDir } returns cache
        return SavedMediaLibraryViewModel(
            library,
            files,
            downloads,
            FakeSettingsRepository(),
            FakeBoardRepository(),
            context,
        )
    }

    private fun record(
        id: String,
        name: String,
    ) = MediaLibraryRecord(
        id,
        "fourchan",
        "g",
        sourceUrl = "https://offline.invalid/$name",
        fileName = name,
        mediaType = if (name.endsWith("webm")) MediaType.VIDEO else MediaType.IMAGE,
        storageKey = id,
    )

    private class LocalFiles(
        private val failKey: String? = null,
    ) : ManagedMediaFiles {
        val removed = mutableListOf<String>()

        override suspend fun contains(storageKey: String) = true

        override suspend fun size(storageKey: String): Long = 10

        override suspend fun importFile(
            storageKey: String,
            sourcePath: String,
        ) = Unit

        override suspend fun exportFile(
            storageKey: String,
            destinationPath: String,
        ): Boolean {
            val destination = File(destinationPath)
            destination.parentFile!!.mkdirs()
            destination.writeText("owned-${destination.name}")
            return true
        }

        override suspend fun remove(storageKey: String) {
            check(storageKey != failKey) { "Cannot remove owned bytes" }
            removed += storageKey
        }
    }
}
