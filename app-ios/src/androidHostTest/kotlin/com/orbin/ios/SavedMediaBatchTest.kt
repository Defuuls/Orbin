package com.orbin.ios

import com.orbin.core.model.MediaLibraryRecord
import com.orbin.domain.repository.InMemoryMediaLibraryRepository
import com.orbin.domain.repository.ManagedMediaFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SavedMediaBatchTest {
    @Test
    fun failedAndRevokedSelectionsDoNotStopLaterItemsOrInflateSuccessCount() =
        runTest {
            val records = listOf(record("first"), record("broken"), record("last")).associateBy { it.id }
            val visited = mutableListOf<String>()
            val progress = mutableListOf<Int>()
            val result =
                runSavedMediaBatch(
                    listOf("first", "revoked", "broken", "last", "first"),
                    resolve = { records[it] },
                    onProgress = { completed, _ -> progress += completed },
                ) {
                    visited += it.id
                    if (it.id == "broken") error("Original is missing")
                }
            assertEquals(listOf("first", "broken", "last"), visited)
            assertEquals(listOf(1, 1, 1, 2), progress)
            assertEquals(2, result.completed)
            assertEquals(4, result.total)
            assertEquals(listOf("Saved media is no longer available", "Original is missing"), result.failures)
        }

    @Test
    fun cancellationDoesNotBecomeAnOrdinaryFailedFile() =
        runTest {
            val visited = mutableListOf<String>()
            assertFailsWith<CancellationException> {
                runSavedMediaBatch(listOf("first", "last"), { record(it) }, { _, _ -> }) {
                    visited += it.id
                    throw CancellationException("Destination closed")
                }
            }
            assertEquals(listOf("first"), visited)
        }

    @Test
    fun deletingOneImportedAliasRetainsOtherRecordsOriginalAndThumbnail() =
        runTest {
            val repository = InMemoryMediaLibraryRepository()
            val files = Files()
            val first = record("first").copy(storageKey = "shared", thumbnailStorageKey = "thumbnail")
            val second = record("second").copy(storageKey = "shared", thumbnailStorageKey = "thumbnail")
            repository.putAll(listOf(first, second))
            deleteSavedMediaRecord(repository, files, first)
            assertEquals(emptyList(), files.removed)
            assertNull(repository.get(first.id))
            assertEquals(second, repository.get(second.id))
            deleteSavedMediaRecord(repository, files, second)
            assertEquals(listOf("shared", "thumbnail"), files.removed)
            assertNull(repository.get(second.id))
        }

    private fun record(id: String) = MediaLibraryRecord(id, "site", fileName = "$id.jpg", storageKey = id)

    private class Files : ManagedMediaFiles {
        val removed = mutableListOf<String>()

        override suspend fun contains(storageKey: String) = true

        override suspend fun size(storageKey: String): Long = 3

        override suspend fun importFile(
            storageKey: String,
            sourcePath: String,
        ) = Unit

        override suspend fun exportFile(
            storageKey: String,
            destinationPath: String,
        ) = true

        override suspend fun remove(storageKey: String) {
            removed += storageKey
        }
    }
}
