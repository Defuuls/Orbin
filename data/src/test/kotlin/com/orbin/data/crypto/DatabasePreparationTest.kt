package com.orbin.data.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Key-loss intent belongs to one key epoch, and each physical database consumes it exactly once. */
class DatabasePreparationTest {
    @Test
    fun repeatedProvidersDoNotResetFilesAgainAndEachDatabaseStillGetsItsOwnReset() {
        val key = DatabasePassphrase.Resolved(byteArrayOf(1), wipeDatabase = true)
        val resets = mutableListOf<String>()
        repeat(2) { key.prepareDatabaseOnce("room") { resets += "room" } }
        repeat(2) { key.prepareDatabaseOnce("media") { resets += "media" } }
        assertThat(resets).containsExactly("room", "media").inOrder()
    }

    @Test
    fun genuineKeyLossCreatesANewResetEpochAndAFailedResetIsNotConsumed() {
        val old = DatabasePassphrase.Resolved(byteArrayOf(1), wipeDatabase = true)
        var resets = 0
        old.prepareDatabaseOnce("room") { resets++ }
        val replacement = DatabasePassphrase.Resolved(byteArrayOf(2), wipeDatabase = true)
        assertThrows(IllegalStateException::class.java) {
            replacement.prepareDatabaseOnce("room") { error("disk unavailable") }
        }
        replacement.prepareDatabaseOnce("room") { resets++ }
        replacement.prepareDatabaseOnce("room") { error("must not unlink a reopened file") }
        assertThat(resets).isEqualTo(2)
    }

    @Test
    fun concurrentGraphInitializationResetsTheFileOnlyOnce() {
        val key = DatabasePassphrase.Resolved(byteArrayOf(1), wipeDatabase = true)
        val resets = AtomicInteger()
        val workers = Executors.newFixedThreadPool(4)
        try {
            val tasks = (1..8).map { Callable { key.prepareDatabaseOnce("room") { resets.incrementAndGet() } } }
            workers.invokeAll(tasks).forEach { it.get(5, TimeUnit.SECONDS) }
            assertThat(resets.get()).isEqualTo(1)
        } finally {
            workers.shutdownNow()
        }
    }

    @Test
    fun retainedOrUnsavedKeysNeverResetPersistentFiles() {
        val retained = DatabasePassphrase.Resolved(byteArrayOf(1), wipeDatabase = false)
        val unsaved = DatabasePassphrase.Resolved(byteArrayOf(2), wipeDatabase = true, persisted = false)
        retained.prepareDatabaseOnce("room") { error("retained keys keep data") }
        unsaved.prepareDatabaseOnce("room") { error("unsaved keys leave disk untouched") }
    }
}
