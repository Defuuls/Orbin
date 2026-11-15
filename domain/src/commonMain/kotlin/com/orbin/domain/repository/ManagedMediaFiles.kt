package com.orbin.domain.repository

/**
 * Owned saved-media bytes. Storage keys are opaque single basenames, never source URLs or paths.
 *
 * Imports and exports stream through platform files rather than materialising a library in memory.
 * An import commits one file atomically; callers validate a complete backup before importing and
 * commit its metadata only after the corresponding bytes are present. Export destinations belong
 * to the caller and must be removed after sharing or packaging the backup.
 */
interface ManagedMediaFiles {
    suspend fun contains(storageKey: String): Boolean

    suspend fun size(storageKey: String): Long?

    suspend fun importFile(
        storageKey: String,
        sourcePath: String,
    )

    suspend fun exportFile(
        storageKey: String,
        destinationPath: String,
    ): Boolean

    suspend fun remove(storageKey: String)
}

/** Reject path separators, traversal, empty names, and control characters at every platform boundary. */
fun validManagedMediaKey(key: String): Boolean =
    key.isNotBlank() &&
        key.length <= MAX_MANAGED_MEDIA_KEY_LENGTH &&
        key != "." &&
        key != ".." &&
        key.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }

private const val MAX_MANAGED_MEDIA_KEY_LENGTH = 200
