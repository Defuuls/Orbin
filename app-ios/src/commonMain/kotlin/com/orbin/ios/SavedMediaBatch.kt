package com.orbin.ios

import com.orbin.core.model.MediaLibraryRecord
import com.orbin.domain.repository.ManagedMediaFiles
import com.orbin.domain.repository.MediaLibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal data class SavedMediaBatchResult(
    val completed: Int,
    val total: Int,
    val failures: List<String>,
)

/** Resolve each identity immediately before use: a selection is not a snapshot of permission. */
internal suspend fun runSavedMediaBatch(
    ids: List<String>,
    resolve: suspend (String) -> MediaLibraryRecord?,
    onProgress: (completed: Int, total: Int) -> Unit,
    action: suspend (MediaLibraryRecord) -> Unit,
): SavedMediaBatchResult {
    val identities = ids.distinct()
    val failures = mutableListOf<String>()
    var completed = 0
    identities.forEach { id ->
        try {
            val record = checkNotNull(resolve(id)) { "Saved media is no longer available" }
            action(record)
            completed++
        } catch (failure: CancellationException) {
            throw failure
        } catch (
            // Platform file/export boundaries report per-item failures without swallowing cancellation.
            @Suppress("TooGenericExceptionCaught") failure: Exception,
        ) {
            failures += failure.message ?: "Could not complete saved media action"
        }
        onProgress(completed, identities.size)
    }
    return SavedMediaBatchResult(completed, identities.size, failures)
}

/** Imported libraries may deliberately reference one owned original from several metadata rows. */
internal suspend fun deleteSavedMediaRecord(
    repository: MediaLibraryRepository,
    files: ManagedMediaFiles,
    record: MediaLibraryRecord,
) {
    val referencedKeys =
        repository
            .observeAll()
            .first()
            .asSequence()
            .filter { it.id != record.id }
            .flatMap { sequenceOf(it.storageKey, it.thumbnailStorageKey).filterNotNull() }
            .toSet()
    listOfNotNull(record.storageKey, record.thumbnailStorageKey)
        .distinct()
        .filterNot(referencedKeys::contains)
        .forEach { files.remove(it) }
    repository.remove(record.id)
}
