package com.orbin.data.diagnostics

import okio.FileSystem
import okio.Path
import kotlin.time.Clock

/**
 * Stores crash reports as individual encrypted files, newest kept and oldest pruned.
 *
 * One file per crash rather than one appended log: a crash can arrive while the process is dying,
 * and a partial write then corrupts only its own report instead of every earlier one. Encryption
 * keeps the project's claim that a copy of the app's data directory yields only ciphertext true —
 * a plaintext crash log sitting next to an encrypted database would quietly weaken it.
 *
 * [encrypt] and [decrypt] are injected rather than calling the Keystore directly so this is
 * testable off-device.
 */
class CrashLogStore(
    private val directory: Path,
    private val encrypt: (ByteArray) -> ByteArray,
    private val decrypt: (ByteArray) -> ByteArray,
    private val fileSystem: FileSystem = com.orbin.data.util.defaultFileSystem,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    /**
     * Writes [report]. Called from an uncaught-exception handler on a thread that is about to die,
     * so it is synchronous and swallows its own failures — a diagnostics write must never replace
     * the crash the user actually needs to see.
     */
    fun record(report: String) {
        runCatching {
            fileSystem.createDirectories(directory)
            val file = directory / "$FILE_PREFIX${now()}$FILE_SUFFIX"
            fileSystem.write(file) {
                write(encrypt(report.encodeToByteArray()))
            }
            prune()
        }
    }

    /** Every readable report, newest first. Unreadable files are skipped rather than failing. */
    fun readAll(): List<String> =
        reportFiles()
            .mapNotNull { file ->
                runCatching {
                    val bytes = fileSystem.read(file) { readByteArray() }
                    decrypt(bytes).decodeToString()
                }.getOrNull()
            }

    fun clear() {
        runCatching {
            reportFiles().forEach { fileSystem.delete(it) }
        }
    }

    private fun reportFiles(): List<Path> =
        fileSystem
            .listOrNull(directory)
            ?.filter { it.name.startsWith(FILE_PREFIX) && it.name.endsWith(FILE_SUFFIX) }
            ?.sortedByDescending { it.name }
            .orEmpty()

    private fun prune() {
        reportFiles().drop(MAX_REPORTS).forEach {
            runCatching { fileSystem.delete(it) }
        }
    }

    private companion object {
        const val FILE_PREFIX = "crash-"
        const val FILE_SUFFIX = ".bin"

        /** Enough to show a pattern across a crash loop, not so many that the directory grows. */
        const val MAX_REPORTS = 5
    }
}
