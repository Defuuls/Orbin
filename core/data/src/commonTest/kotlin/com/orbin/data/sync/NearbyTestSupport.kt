package com.orbin.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// In-memory stand-ins shared by the nearby-sync tests in commonTest and the relay end-to-end test.

internal class Progress(
    private var document: SyncDocument,
) : NearbyProgress {
    private val mutex = Mutex()

    val snapshotPause = MutableStateFlow<Pair<CompletableDeferred<Unit>, CompletableDeferred<Unit>>?>(null)

    override suspend fun snapshot(): SyncDocument {
        snapshotPause.value?.let { (entered, release) ->
            entered.complete(Unit)
            release.await()
        }
        return mutex.withLock { document }
    }

    override suspend fun merge(
        base: SyncDocument?,
        remote: SyncDocument,
    ): SyncDocument =
        mutex.withLock {
            mergeSync(base, document, remote).also { document = it }
        }

    suspend fun replace(value: SyncDocument) = mutex.withLock { document = value }
}

internal class MemorySecret : SyncPasswordStore {
    private var value: String? = null

    override suspend fun read(): String? = value

    override suspend fun write(password: String) {
        value = password
    }
}

internal class MemoryPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow<Preferences>(emptyPreferences())
    private val mutex = Mutex()

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        mutex.withLock {
            transform(data.value).also { data.value = it }
        }
}
