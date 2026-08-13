package com.orbin.data.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The cipher is injected, so these run on the JVM without the Android Keystore. A reversible
 * stand-in still proves the store round-trips through whatever cipher it is given.
 */
class CrashLogStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private var clock = 1_000L

    private fun store(directory: File = temporaryFolder.root) =
        CrashLogStore(
            directory = directory,
            encrypt = { bytes -> bytes.map { (it + 1).toByte() }.toByteArray() },
            decrypt = { bytes -> bytes.map { (it - 1).toByte() }.toByteArray() },
            now = { clock++ },
        )

    @Test
    fun `records a report and reads it back through the cipher`() {
        val store = store()

        store.record("boom")

        assertThat(store.readAll()).containsExactly("boom")
    }

    @Test
    fun `stores reports on disk encrypted, not as plain text`() {
        store().record("secret trace")

        val written =
            temporaryFolder.root
                .listFiles()
                .orEmpty()
                .single()
                .readBytes()

        assertThat(written.decodeToString()).doesNotContain("secret trace")
    }

    @Test
    fun `keeps the newest reports and prunes the rest`() {
        val store = store()

        repeat(8) { index -> store.record("crash $index") }

        // Five is the cap; the three oldest should be gone.
        assertThat(store.readAll()).hasSize(5)
        assertThat(store.readAll()).contains("crash 7")
        assertThat(store.readAll()).doesNotContain("crash 0")
    }

    @Test
    fun `returns reports newest first`() {
        val store = store()

        store.record("older")
        store.record("newer")

        assertThat(store.readAll().first()).isEqualTo("newer")
    }

    @Test
    fun `skips an unreadable report instead of failing the whole read`() {
        val store = store()
        store.record("good")
        File(temporaryFolder.root, "crash-9999.bin").writeBytes(ByteArray(0))

        // A zero-length file cannot be a valid report; the readable one must still come back.
        assertThat(store.readAll()).contains("good")
    }

    @Test
    fun `clear removes everything`() {
        val store = store()
        store.record("one")
        store.record("two")

        store.clear()

        assertThat(store.readAll()).isEmpty()
    }

    @Test
    fun `recording never throws when the directory cannot be created`() {
        val blocked = File(temporaryFolder.newFile("not-a-directory"), "nested")

        // A diagnostics write must never replace the crash the user actually needs to see.
        store(blocked).record("boom")
    }
}
