package com.orbin.ios

import com.orbin.core.model.MediaLibraryRecord
import com.orbin.domain.repository.ManagedMediaFiles

/** Platform-owned staging paths are the only local files handed to the offline viewer. */
internal expect suspend fun stageSavedMedia(
    files: ManagedMediaFiles,
    record: MediaLibraryRecord,
): String

/**
 * Writes a file the app fetched itself into the same staging area, for playback under strict
 * encrypted DNS. Released with [releaseSavedMedia] like any other staged file.
 */
internal expect suspend fun stageFetchedMedia(
    bytes: ByteArray,
    fileName: String,
): String

internal expect suspend fun releaseSavedMedia(path: String)

internal expect fun savedMediaFileUrl(path: String): String

internal expect fun shareSavedMedia(
    paths: List<String>,
    onFinished: () -> Unit,
): Boolean

internal expect suspend fun exportSavedMedia(
    path: String,
    record: MediaLibraryRecord,
)
