package com.orbin.data.diagnostics

import java.io.File

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
internal class CrashLogStore(
    private val directory: File,
    private val encrypt: (ByteArray) -> ByteArray,
    private val decrypt: (ByteArray) -> ByteArray,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * Writes [report]. Called from an uncaught-exception handler on a thread that is about to die,
     * so it is synchronous and swallows its own failures — a diagnostics write must never replace
     * the crash the user actually needs to see.
     */
    fun record(report: String) {
        runCatching {
            directory.mkdirs()
            File(directory, "$FILE_PREFIX${now()}$FILE_SUFFIX").writeBytes(encrypt(report.toByteArray()))
            prune()
        }
    }

    /** Every readable report, newest first. Unreadable files are skipped rather than failing. */
    fun readAll(): List<String> =
        reportFiles()
            .mapNotNull { file -> runCatching { decrypt(file.readBytes()).decodeToString() }.getOrNull() }

    fun clear() {
        runCatching { reportFiles().forEach { it.delete() } }
    }

    private fun reportFiles(): List<File> =
        directory
            .listFiles { file -> file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX) }
            ?.sortedByDescending { it.name }
            .orEmpty()

    private fun prune() {
        reportFiles().drop(MAX_REPORTS).forEach { it.delete() }
    }

    private companion object {
        const val FILE_PREFIX = "crash-"
        const val FILE_SUFFIX = ".bin"

        /** Enough to show a pattern across a crash loop, not so many that the directory grows. */
        const val MAX_REPORTS = 5
    }
}
