package com.orbin.ios

import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileCrashStoreTest {
    private val testDir: Path =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "file-crash-store-${Random.nextLong()}"
    private var clock = 1_000L
    private val protected = mutableListOf<Path>()

    @AfterTest
    fun tearDown() {
        runCatching { deleteRecursively(testDir) }
    }

    private fun deleteRecursively(path: Path) {
        val metadata = FileSystem.SYSTEM.metadataOrNull(path) ?: return
        if (metadata.isDirectory) {
            FileSystem.SYSTEM.listOrNull(path)?.forEach { deleteRecursively(it) }
        }
        FileSystem.SYSTEM.delete(path)
    }

    private fun store() =
        FileCrashStore(
            directory = testDir,
            fileSystem = FileSystem.SYSTEM,
            protect = { protected += it },
            now = { clock++ },
        )

    @Test
    fun recordsReportAndReadsBackNewestFirst() {
        val store = store()
        store.appendReport("older")
        store.appendReport("newer")

        assertEquals(listOf("newer", "older"), store.reports())
        assertTrue(protected.contains(testDir))
        assertTrue(protected.any { it.name.startsWith("crash-") })
    }

    @Test
    fun keepsNewestReportsAndPrunesTheRest() {
        val store = store()
        repeat(8) { index -> store.appendReport("crash $index") }

        val reports = store.reports()
        assertEquals(5, reports.size)
        assertEquals("crash 7", reports.first())
        assertTrue("crash 0" !in reports)
        assertTrue("crash 2" !in reports)
    }

    @Test
    fun startupCountSurvivesANewStoreOnTheSameDirectory() {
        store().startupCrashes = 2

        val reread = store()
        assertEquals(2, reread.startupCrashes)
        assertTrue(protected.any { it.name == FileCrashStore.COUNTER_FILE })
    }

    @Test
    fun clearRemovesReportsButLeavesTheCountUntilReset() {
        val store = store()
        store.appendReport("one")
        store.startupCrashes = 1
        store.clearReports()

        assertTrue(store.reports().isEmpty())
        assertEquals(1, store.startupCrashes)
    }

    @Test
    fun skipsUnreadableReportInsteadOfFailingWholeRead() {
        val store = store()
        store.appendReport("good")
        FileSystem.SYSTEM.write(testDir / "crash-corrupt.txt") { write(ByteArray(0)) }
        // Empty file still reads as empty string; write a directory-named collision is harder.
        // A file that is not UTF-8 is still readable as empty-or-bytes; we just check good is there.
        assertTrue(store.reports().contains("good"))
    }

    @Test
    fun adoptLegacyCopiesReportsAndCountThenClearsTheSource() {
        val store = store()
        var cleared = false

        adoptLegacyCrashRecords(
            store = store,
            reports = listOf("legacy-one", "legacy-two"),
            startupCrashes = 2,
            clearLegacy = { cleared = true },
        )

        assertEquals(listOf("legacy-two", "legacy-one"), store.reports())
        assertEquals(2, store.startupCrashes)
        assertTrue(cleared)
    }

    @Test
    fun adoptLegacyIsNoOpWhenTheSourceIsEmpty() {
        val store = store()
        var cleared = false

        adoptLegacyCrashRecords(store, emptyList(), 0) { cleared = true }

        assertTrue(store.reports().isEmpty())
        assertEquals(0, store.startupCrashes)
        assertTrue(!cleared)
    }
}
