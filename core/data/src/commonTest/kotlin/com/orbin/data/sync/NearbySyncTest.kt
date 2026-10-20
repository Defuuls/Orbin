package com.orbin.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real sockets and real AEAD, with only persistence and local progress replaced by in-memory stores. */
class NearbySyncTest {
    @Test
    fun pairsMergesReusesKeysPropagatesDeletionAndRevokesAccess() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val firstProgress = Progress(doc(1))
            val secondProgress = Progress(doc(2))
            val first = controller(firstProgress, scope)
            val second = controller(secondProgress, scope)
            try {
                first.setActive(true)
                second.setActive(true)
                first.createPairingCode()
                val code = assertNotNull(first.state.value.pairingCode)
                second.join(code)
                withTimeout(5000) { first.state.first { it.devices.firstOrNull()?.lastSyncedMillis != null } }
                assertEquals(
                    setOf(1L, 2L),
                    firstProgress
                        .snapshot()
                        .visits
                        .map { it.thread }
                        .toSet(),
                )
                assertEquals(firstProgress.snapshot().visits.toSet(), secondProgress.snapshot().visits.toSet())
                assertNull(first.state.value.pairingCode)
                assertEquals(1, first.state.value.devices.size)
                assertEquals(1, second.state.value.devices.size)

                secondProgress.replace(doc(2))
                delay(20)
                second.syncNow()
                withTimeout(5000) { while (firstProgress.snapshot().visits.size != 1) delay(10) }
                assertEquals(listOf(2L), firstProgress.snapshot().visits.map { it.thread })

                first.forget(
                    first.device().id,
                )
                assertTrue(
                    first.state.value.devices
                        .isEmpty(),
                )
                secondProgress.replace(doc(3))
                second.syncNow()
                assertEquals(listOf(2L), firstProgress.snapshot().visits.map { it.thread })
            } finally {
                first.setActive(false)
                second.setActive(false)
                scope.cancel()
            }
        }

    @Test
    fun consumedInvitationCannotEnrollAnotherDeviceAndBackgroundingClearsCodes() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val first = controller(Progress(doc(1)), scope)
            val second = controller(Progress(doc(2)), scope)
            val third = controller(Progress(doc(3)), scope)
            try {
                listOf(first, second, third).forEach { it.setActive(true) }
                first.createPairingCode()
                val code = assertNotNull(first.state.value.pairingCode)
                second.join(code)
                withTimeout(5000) { first.state.first { it.devices.firstOrNull()?.lastSyncedMillis != null } }
                third.join(code)
                assertTrue(
                    third.state.value.devices
                        .isEmpty(),
                )
                assertEquals(1, first.state.value.devices.size)
                first.createPairingCode()
                assertNotNull(first.state.value.pairingCode)
                first.setActive(false)
                assertNull(first.state.value.pairingCode)
                assertFalse(first.state.value.listening)
            } finally {
                listOf(first, second, third).forEach { it.setActive(false) }
                scope.cancel()
            }
        }

    @Test
    fun automaticSyncRequiresMutualTrustAndCurrentLocalNetwork() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val sameNetwork = MutableStateFlow(false)
            val networks = {
                if (sameNetwork.value) {
                    listOf(NearbyNetwork("127.0.0.1", 8))
                } else {
                    listOf(NearbyNetwork("192.168.2.3", 24))
                }
            }
            val firstProgress = Progress(doc(1))
            val secondProgress = Progress(doc(2))
            val secret = MemorySecret()
            val first = controller(firstProgress, scope, networks, secret)
            val second = controller(secondProgress, scope, networks)
            try {
                first.setActive(true)
                second.setActive(true)
                first.createPairingCode()
                second.join(assertNotNull(first.state.value.pairingCode))
                withTimeout(5000) { first.state.first { it.devices.firstOrNull()?.lastSyncedMillis != null } }
                val secondId =
                    first.device().id
                val firstId =
                    second.device().id
                assertFalse(
                    first.device().trusted,
                )
                firstProgress.replace(doc(3))
                sameNetwork.value = true
                delay(200)
                assertFalse(secondProgress.snapshot().visits.any { it.thread == 3L })
                first.setTrusted(secondId, true)
                delay(200)
                assertFalse(secondProgress.snapshot().visits.any { it.thread == 3L })
                sameNetwork.value = false
                second.setTrusted(firstId, true)
                delay(200)
                assertFalse(secondProgress.snapshot().visits.any { it.thread == 3L })

                // Trust survives repository recreation and does not need another invitation.
                val restored = controller(Progress(doc(1)), scope, networks, secret)
                restored.setEnabled(true)
                assertTrue(
                    restored.device().trusted,
                )
                sameNetwork.value = true
                withTimeout(5000) { while (secondProgress.snapshot().visits.none { it.thread == 3L }) delay(10) }
                assertTrue(
                    first.device().trusted,
                )
                assertTrue(
                    second.device().trusted,
                )

                // Revoking trust blocks automatic writes regardless of which device is the initiator.
                first.setTrusted(secondId, false)
                firstProgress.replace(doc(4))
                delay(200)
                assertFalse(secondProgress.snapshot().visits.any { it.thread == 4L })
                first.syncNow()
                withTimeout(5000) { while (secondProgress.snapshot().visits.none { it.thread == 4L }) delay(10) }
                assertFalse(
                    first.device().trusted,
                )
            } finally {
                first.setActive(false)
                second.setActive(false)
                scope.cancel()
            }
        }

    private fun NearbySync.device() = state.value.devices.single()

    private fun controller(
        progress: Progress,
        scope: CoroutineScope,
        networks: () -> List<NearbyNetwork> = { listOf(NearbyNetwork("127.0.0.1", 8)) },
        secret: MemorySecret = MemorySecret(),
    ) = NearbySync(
        progress,
        MemoryPreferences(),
        NearbySyncSecrets(secret),
        scope,
        { listOf("127.0.0.1") },
        0,
        networks = networks,
        autoSyncMillis = 40,
    )

    private fun doc(thread: Long) =
        SyncDocument(visits = listOf(SyncedVisit("site", "g", thread, "Title", lastVisitedMillis = 1)))

    private class Progress(
        private var document: SyncDocument,
    ) : NearbyProgress {
        private val mutex = Mutex()

        override suspend fun snapshot(): SyncDocument = mutex.withLock { document }

        override suspend fun merge(
            base: SyncDocument?,
            remote: SyncDocument,
        ): SyncDocument =
            mutex.withLock {
                mergeSync(base, document, remote).also { document = it }
            }

        suspend fun replace(value: SyncDocument) = mutex.withLock { document = value }
    }

    private class MemorySecret : SyncPasswordStore {
        private var value: String? = null

        override suspend fun read(): String? = value

        override suspend fun write(password: String) {
            value = password
        }
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow<Preferences>(emptyPreferences())
        private val mutex = Mutex()

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock {
                transform(data.value).also { data.value = it }
            }
    }
}
