package com.orbin.data.diagnostics

import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashLogStoreTest {
    private val testDir: Path =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "crash-log-test-${Random.nextLong()}"
    private var clock = 1_000L

    @AfterTest
    fun tearDown() {
        runCatching {
            deleteRecursively(testDir)
        }
    }

    private fun deleteRecursively(path: Path) {
        val metadata = FileSystem.SYSTEM.metadataOrNull(path) ?: return
        if (metadata.isDirectory) {
            FileSystem.SYSTEM.listOrNull(path)?.forEach { deleteRecursively(it) }
        }
        FileSystem.SYSTEM.delete(path)
    }

    private fun store(directory: Path = testDir) =
        CrashLogStore(
            directory = directory,
            encrypt = { bytes -> bytes.map { (it + 1).toByte() }.toByteArray() },
            decrypt = { bytes -> bytes.map { (it - 1).toByte() }.toByteArray() },
            fileSystem = FileSystem.SYSTEM,
            now = { clock++ },
        )

    @Test
    fun recordsReportAndReadsBackThroughCipher() {
        val store = store()
        store.record("boom")

        assertEquals(listOf("boom"), store.readAll())
    }

    @Test
    fun storesReportsOnDiskEncryptedNotAsPlainText() {
        store().record("secret trace")

        val files = FileSystem.SYSTEM.listOrNull(testDir).orEmpty()
        assertEquals(1, files.size)
        val written = FileSystem.SYSTEM.read(files.single()) { readByteArray() }

        assertFalse(written.decodeToString().contains("secret trace"))
    }

    @Test
    fun keepsNewestReportsAndPrunesTheRest() {
        val store = store()
        repeat(8) { index -> store.record("crash $index") }

        val reports = store.readAll()
        assertEquals(5, reports.size)
        assertTrue(reports.contains("crash 7"))
        assertFalse(reports.contains("crash 0"))
    }

    @Test
    fun returnsReportsNewestFirst() {
        val store = store()
        store.record("older")
        store.record("newer")

        assertEquals("newer", store.readAll().first())
    }

    @Test
    fun skipsUnreadableReportInsteadOfFailingWholeRead() {
        val store = store()
        store.record("good")

        val corruptFile = testDir / "crash-9999.bin"
        FileSystem.SYSTEM.write(corruptFile) { write(ByteArray(0)) }

        assertEquals(listOf("good"), store.readAll())
    }

    @Test
    fun clearRemovesEverything() {
        val store = store()
        store.record("one")
        store.record("two")

        store.clear()

        assertTrue(store.readAll().isEmpty())
    }
}
