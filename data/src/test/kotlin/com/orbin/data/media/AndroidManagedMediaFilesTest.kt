package com.orbin.data.media

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator

class AndroidManagedMediaFilesTest {
    @Test
    fun originalsAreEncryptedAndExportsMatchTheExactSourceBytes() =
        runBlocking {
            withStorage { root, storage ->
                val source =
                    File(
                        root,
                        "source.webm",
                    ).apply { writeBytes(ByteArray(2_000_123) { (it % 251).toByte() }) }
                storage.importFile("original", source.path)
                assertTrue(storage.contains("original"))
                assertEquals(source.length(), storage.size("original"))
                assertFalse(source.readBytes().contentEquals(File(root, "owned/original").readBytes()))
                val exported = File(root, "share/video.webm")
                assertTrue(storage.exportFile("original", exported.path))
                assertArrayEquals(source.readBytes(), exported.readBytes())
                storage.remove("original")
                assertFalse(storage.contains("original"))
            }
        }

    @Test
    fun authenticationFailureNeverPublishesPartialPlaintext() =
        runBlocking {
            withStorage { root, storage ->
                val source = File(root, "source").apply { writeText("a private saved original") }
                storage.importFile("original", source.path)
                val encrypted = File(root, "owned/original")
                encrypted.writeBytes(
                    encrypted.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() },
                )
                val target = File(root, "share/original")
                val failure = runCatching { storage.exportFile("original", target.path) }
                assertTrue(failure.isFailure)
                assertFalse(target.exists())
                assertTrue(
                    checkNotNull(target.parentFile)
                        .listFiles()
                        .orEmpty()
                        .isEmpty(),
                )
            }
        }

    @Test
    fun invalidKeysAndFailedReplacementLeaveTheOriginalIntact() =
        runBlocking {
            withStorage { root, storage ->
                val source = File(root, "source").apply { writeText("keep me") }
                storage.importFile("original", source.path)
                assertTrue(runCatching { storage.importFile("original", File(root, "missing").path) }.isFailure)
                listOf("", "..", "../escape", "a/b", "a\\b", "/absolute").forEach { key ->
                    assertTrue(
                        runCatching {
                            storage.importFile(
                                key,
                                source.path,
                            )
                        }.exceptionOrNull() is IllegalArgumentException,
                    )
                }
                val target = File(root, "export")
                assertTrue(storage.exportFile("original", target.path))
                assertEquals("keep me", target.readText())
                assertFalse(File(root, "owned").listFiles().orEmpty().any { it.name.endsWith(".part") })
            }
        }

    private suspend fun withStorage(block: suspend (File, AndroidManagedMediaFiles) -> Unit) {
        val directory = Files.createTempDirectory("orbin-owned-media").toFile()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        try {
            block(directory, AndroidManagedMediaFiles(File(directory, "owned")) { key })
        } finally {
            directory.deleteRecursively()
        }
    }
}
