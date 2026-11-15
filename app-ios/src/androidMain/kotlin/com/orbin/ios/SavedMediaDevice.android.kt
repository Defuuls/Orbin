package com.orbin.ios

import com.orbin.core.model.MediaLibraryRecord
import com.orbin.domain.repository.ManagedMediaFiles

internal actual suspend fun stageSavedMedia(
    files: ManagedMediaFiles,
    record: MediaLibraryRecord,
): String = error("Native saved-media staging is unavailable in the host-test target")

internal actual suspend fun releaseSavedMedia(path: String) = Unit

internal actual fun savedMediaFileUrl(path: String): String = "file://$path"

internal actual fun shareSavedMedia(
    paths: List<String>,
    onFinished: () -> Unit,
): Boolean = false

internal actual suspend fun exportSavedMedia(
    path: String,
    record: MediaLibraryRecord,
) = Unit
