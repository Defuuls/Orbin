package com.orbin.ios

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CloudSyncTest {
    @Test
    fun `merge is deterministic and deletion beats an equally versioned update`() {
        val original = item("a", 1)
        val changed = item("a", 2)
        val deleted = changed.copy(deleted = true)
        val otherSite = item("other-site-a", 1)
        assertEquals(
            listOf(deleted, otherSite),
            mergeCloudItems(listOf(original, deleted), listOf(changed, otherSite)),
        )
        assertEquals(
            mergeCloudItems(listOf(changed), listOf(deleted)),
            mergeCloudItems(listOf(deleted), listOf(changed)),
        )
    }

    @Test
    fun `sync is off until opt in and restores remote data before uploading local changes`() =
        runTest {
            val events = mutableListOf<String>()
            val local = FakeLocal(listOf(item("local", 1)), events)
            val remote = FakeTransport(listOf(item("remote", 2)), events)
            var enabled = false
            val sync = CloudSyncController(local, remote, this, { true }, { enabled }, { enabled = it })
            sync.refreshAvailability()
            advanceUntilIdle()
            sync.syncNow()
            advanceUntilIdle()
            assertTrue(events.isEmpty())
            sync.setEnabled(true)
            advanceUntilIdle()
            assertEquals(listOf("fetch", "apply:remote", "save:local", "ack"), events)
            assertEquals(setOf("local", "remote"), local.items.map { it.id }.toSet())
            assertTrue(enabled)
            assertEquals("Up to date", sync.state.value.detail)
        }

    @Test
    fun `locking during fetch prevents import and upload`() =
        runTest {
            val events = mutableListOf<String>()
            var authorized = true
            val local = FakeLocal(listOf(item("local", 1)), events)
            val remote = FakeTransport(listOf(item("remote", 2)), events, afterFetch = { authorized = false })
            val sync = CloudSyncController(local, remote, this, { authorized }, { true }, {})
            sync.refreshAvailability()
            advanceUntilIdle()
            sync.syncNow()
            advanceUntilIdle()
            assertEquals(listOf("fetch"), events)
            assertEquals(listOf("local"), local.items.map { it.id })
            assertFalse(sync.state.value.working)
        }

    @Test
    fun `failed upload leaves data retryable and is never acknowledged`() =
        runTest {
            val events = mutableListOf<String>()
            val local = FakeLocal(listOf(item("local", 1)), events)
            val remote = FakeTransport(emptyList(), events, refuseSave = true)
            val sync = CloudSyncController(local, remote, this, { true }, { true }, {})
            sync.refreshAvailability()
            advanceUntilIdle()
            sync.syncNow()
            advanceUntilIdle()
            assertEquals(listOf("fetch", "save:local"), events)
            assertEquals(1, local.items.size)
            assertTrue(
                sync.state.value.detail
                    .startsWith("Sync incomplete"),
            )
        }

    @Test
    fun `old lease cleanup cannot clear a newer sync working state`() =
        runTest {
            val oldCleanup = CompletableDeferred<Unit>()
            val newFetch = CompletableDeferred<List<CloudSyncItem>>()
            val releases = mutableListOf<Int>()
            var leases = 0
            var enabled = false
            val remote =
                ControlledTransport().apply {
                    leaseFactory = {
                        val lease = ++leases
                        ControlledTransport().apply {
                            if (lease == 2) fetchBlock = { newFetch.await() }
                            releaseBlock = {
                                if (lease == 1) oldCleanup.await()
                                releases += lease
                            }
                        }
                    }
                }
            val sync =
                CloudSyncController(FakeLocal(emptyList(), mutableListOf()), remote, this, { true }, { enabled }, {
                    enabled =
                        it
                })
            sync.refreshAvailability()
            advanceUntilIdle()
            sync.setEnabled(true)
            runCurrent()
            sync.setEnabled(false)
            sync.setEnabled(true)
            runCurrent()
            assertEquals(2, leases)
            assertTrue(sync.state.value.working)

            oldCleanup.complete(Unit)
            runCurrent()
            assertEquals(listOf(1), releases)
            assertTrue(sync.state.value.working)
            assertTrue(
                sync.state.value.detail
                    .startsWith("Syncing"),
            )

            newFetch.complete(emptyList())
            advanceUntilIdle()
            assertEquals(listOf(1, 2), releases)
            assertFalse(sync.state.value.working)
            assertEquals("Up to date", sync.state.value.detail)
        }

    @Test
    fun `foreground waits cancelled fetch and cleanup before retrying without importing stale data`() =
        runTest {
            val oldFetch = CompletableDeferred<Unit>()
            val oldCleanup = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val local = FakeLocal(emptyList(), events)
            var authorized = true
            var availabilityChecks = 0
            var leases = 0
            val remote =
                ControlledTransport().apply {
                    availabilityBlock = {
                        availabilityChecks++
                        CloudAvailability(true, "Ready")
                    }
                    leaseFactory = {
                        val lease = ++leases
                        ControlledTransport().apply {
                            fetchBlock = {
                                if (lease == 1) withContext(NonCancellable) { oldFetch.await() }
                                listOf(item(if (lease == 1) "stale" else "current", 1))
                            }
                            if (lease == 1) releaseBlock = { oldCleanup.await() }
                        }
                    }
                }
            val sync = CloudSyncController(local, remote, this, { authorized }, { true }, {})
            sync.refreshAvailability()
            advanceUntilIdle()
            sync.syncNow()
            runCurrent()
            authorized = false
            sync.onLocked()
            authorized = true
            sync.onForeground()
            runCurrent()
            assertEquals(1, availabilityChecks)
            oldFetch.complete(Unit)
            runCurrent()
            assertTrue(events.isEmpty())
            assertEquals(1, availabilityChecks)

            oldCleanup.complete(Unit)
            advanceUntilIdle()
            assertEquals(2, availabilityChecks)
            assertEquals(listOf("current"), local.items.map { it.id })
            assertEquals(listOf("apply:current", "ack"), events)
            assertEquals("Up to date", sync.state.value.detail)
        }

    @Test
    fun `cancelled account check cannot disable or replace the newly checked account`() =
        runTest {
            val oldAvailability = CompletableDeferred<Unit>()
            var checks = 0
            var authorized = true
            var enabled = true
            var account: String? = "current-account"
            val enabledWrites = mutableListOf<Boolean>()
            val remote =
                ControlledTransport().apply {
                    availabilityBlock = {
                        if (++checks == 1) {
                            withContext(NonCancellable) { oldAvailability.await() }
                            CloudAvailability(true, "Old account", "old-account")
                        } else {
                            CloudAvailability(true, "Ready", "current-account")
                        }
                    }
                }
            val sync =
                CloudSyncController(
                    FakeLocal(emptyList(), mutableListOf()),
                    remote,
                    this,
                    { authorized },
                    { enabled },
                    {
                        enabled = it
                        enabledWrites += it
                    },
                    { account },
                    { account = it },
                )
            sync.refreshAvailability()
            runCurrent()
            authorized = false
            sync.onLocked()
            authorized = true
            sync.refreshAvailability()
            runCurrent()
            sync.syncNow()
            runCurrent()
            assertEquals(2, checks)
            assertEquals("Up to date", sync.state.value.detail)

            oldAvailability.complete(Unit)
            advanceUntilIdle()
            assertEquals(2, checks)
            assertTrue(enabledWrites.isEmpty())
            assertTrue(sync.state.value.enabled)
            assertEquals("Up to date", sync.state.value.detail)
            sync.setEnabled(false)
            sync.setEnabled(true)
            advanceUntilIdle()
            assertEquals("current-account", account)
        }

    @Test
    fun `an actual account switch requires a new explicit opt in`() =
        runTest {
            var enabled = true
            var account: String? = "previous-account"
            var fetches = 0
            val remote =
                ControlledTransport().apply {
                    availabilityBlock = { CloudAvailability(true, "Ready", "new-account") }
                    fetchBlock = {
                        fetches++
                        emptyList()
                    }
                }
            val sync =
                CloudSyncController(
                    FakeLocal(emptyList(), mutableListOf()),
                    remote,
                    this,
                    { true },
                    { enabled },
                    { enabled = it },
                    { account },
                    { account = it },
                )
            sync.onForeground()
            advanceUntilIdle()
            assertFalse(enabled)
            assertFalse(sync.state.value.enabled)
            assertEquals(0, fetches)
            assertEquals("previous-account", account)

            sync.setEnabled(true)
            advanceUntilIdle()
            assertTrue(enabled)
            assertEquals("new-account", account)
            assertEquals(1, fetches)
        }

    @Test
    fun `foreground retries availability and sync after an offline check`() =
        runTest {
            var online = false
            var fetches = 0
            val remote =
                ControlledTransport().apply {
                    availabilityBlock = { CloudAvailability(online, if (online) "Ready" else "Offline", "account") }
                    fetchBlock = {
                        fetches++
                        emptyList()
                    }
                }
            val sync =
                CloudSyncController(
                    FakeLocal(emptyList(), mutableListOf()),
                    remote,
                    this,
                    { true },
                    { true },
                    {},
                    { "account" },
                )
            sync.onForeground()
            advanceUntilIdle()
            assertFalse(sync.state.value.available)
            assertTrue(sync.state.value.enabled)
            assertEquals(0, fetches)

            online = true
            sync.onForeground()
            advanceUntilIdle()
            assertTrue(sync.state.value.available)
            assertTrue(sync.state.value.enabled)
            assertEquals(1, fetches)
            assertEquals("Up to date", sync.state.value.detail)
        }

    private fun item(
        id: String,
        revision: Long,
    ) = CloudSyncItem(id, revision, "device-a", "{}")

    private class FakeLocal(
        var items: List<CloudSyncItem>,
        val events: MutableList<String>,
    ) : CloudSyncLocalStore {
        override suspend fun snapshot() = items

        override suspend fun apply(items: List<CloudSyncItem>) {
            events += "apply:${items.joinToString { it.id }}"
            this.items = mergeCloudItems(this.items, items)
        }

        override suspend fun rememberSynced(items: List<CloudSyncItem>) {
            events += "ack"
        }
    }

    private class FakeTransport(
        val items: List<CloudSyncItem>,
        val events: MutableList<String>,
        val afterFetch: () -> Unit = {},
        val refuseSave: Boolean = false,
    ) : CloudSyncTransport {
        override suspend fun availability() = CloudAvailability(true, "Ready")

        override suspend fun fetch(known: List<CloudSyncItem>): List<CloudSyncItem> {
            events += "fetch"
            afterFetch()
            return items
        }

        override suspend fun save(items: List<CloudSyncItem>) {
            events += "save:${items.joinToString { it.id }}"
            check(!refuseSave) { "Unavailable" }
        }

        override suspend fun deleteAll() {
            events += "deleteAll"
        }
    }

    private class ControlledTransport : CloudSyncTransport {
        var availabilityBlock: suspend () -> CloudAvailability = { CloudAvailability(true, "Ready") }
        var fetchBlock: suspend (List<CloudSyncItem>) -> List<CloudSyncItem> = { emptyList() }
        var saveBlock: suspend (List<CloudSyncItem>) -> Unit = {}
        var deleteBlock: suspend () -> Unit = {}
        var releaseBlock: suspend () -> Unit = {}
        var leaseFactory: () -> CloudSyncTransport = { this }

        override fun newSession() = leaseFactory()

        override suspend fun availability() = availabilityBlock()

        override suspend fun fetch(known: List<CloudSyncItem>) = fetchBlock(known)

        override suspend fun save(items: List<CloudSyncItem>) = saveBlock(items)

        override suspend fun deleteAll() = deleteBlock()

        override suspend fun releaseFetchedAssets() = releaseBlock()
    }
}
