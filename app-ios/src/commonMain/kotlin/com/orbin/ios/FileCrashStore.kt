package com.orbin.ios

import com.orbin.data.util.defaultFileSystem
import okio.FileSystem
import okio.Path
import kotlin.time.Clock

/**
 * Crash reports and the startup-crash counter, kept in files rather than a preferences store:
 * safe mode has to work when the database is what keeps the app from starting, and the files can
 * carry complete iOS Data Protection (or another [protect] callback) which UserDefaults cannot.
 */
internal class FileCrashStore(
    private val directory: Path,
    private val fileSystem: FileSystem = defaultFileSystem,
    private val protect: (Path) -> Unit = {},
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val maxReports: Int = MAX_REPORTS,
) : CrashStore {
    init {
        fileSystem.createDirectories(directory, mustCreate = false)
        protect(directory)
    }

    override var startupCrashes: Int
        get() =
            runCatching {
                fileSystem.read(counterPath) { readUtf8().trim().toInt() }
            }.getOrDefault(0)
        set(value) {
            writeProtected(counterPath, value.toString())
        }

    override fun appendReport(report: String) {
        val path = directory / "crash-${now()}-$nextId.txt"
        writeProtected(path, report)
        prune()
    }

    override fun reports(): List<String> =
        reportFiles().mapNotNull { path ->
            runCatching { fileSystem.read(path) { readUtf8() } }.getOrNull()
        }

    override fun clearReports() {
        reportFiles().forEach { path ->
            runCatching { fileSystem.delete(path) }
        }
    }

    private var nextId = 0

    private val counterPath: Path
        get() = directory / COUNTER_FILE

    private fun reportFiles(): List<Path> =
        fileSystem
            .listOrNull(directory)
            .orEmpty()
            .filter { it.name.startsWith(FILE_PREFIX) && it.name.endsWith(FILE_SUFFIX) }
            .sortedByDescending { it.name }

    private fun prune() {
        reportFiles().drop(maxReports).forEach { path ->
            runCatching { fileSystem.delete(path) }
        }
    }

    private fun writeProtected(
        path: Path,
        text: String,
    ) {
        runCatching {
            fileSystem.write(path) { writeUtf8(text) }
            protect(path)
        }
    }

    companion object {
        const val MAX_REPORTS = 5
        const val COUNTER_FILE = "startup-count.txt"
        private const val FILE_PREFIX = "crash-"
        private const val FILE_SUFFIX = ".txt"
    }
}

/**
 * Copies records out of a previous store (UserDefaults on iOS) and clears the source so the next
 * launch does not append them again. No-op when the source is already empty.
 */
internal fun adoptLegacyCrashRecords(
    store: CrashStore,
    reports: List<String>,
    startupCrashes: Int,
    clearLegacy: () -> Unit,
) {
    if (reports.isEmpty() && startupCrashes == 0) return
    reports.forEach(store::appendReport)
    if (startupCrashes > store.startupCrashes) {
        store.startupCrashes = startupCrashes
    }
    clearLegacy()
}
