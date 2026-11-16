package com.orbin.ios

import com.orbin.uinext.screens.SettingItem
import com.orbin.uinext.screens.SettingKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** A portable entity, never a device credential, session history or installation setting. */
@Serializable
data class CloudSyncItem(
    val id: String,
    val revision: Long,
    val origin: String,
    val payload: String,
    val deleted: Boolean = false,
    val assetPath: String? = null,
    val thumbnailAssetPath: String? = null,
)

/** Implementations retain revisions and tombstones across process death. */
interface CloudSyncLocalStore {
    suspend fun snapshot(): List<CloudSyncItem>

    /** Applies validated remote entities and their revisions atomically within each store. */
    suspend fun apply(items: List<CloudSyncItem>)

    /** Only called once the server has acknowledged all these exact revisions. */
    suspend fun rememberSynced(items: List<CloudSyncItem>)
}

interface CloudSyncTransport {
    /** Isolates a sync's native request cancellation, server records and temporary asset lease. */
    fun newSession(): CloudSyncTransport = this

    suspend fun availability(): CloudAvailability

    suspend fun fetch(known: List<CloudSyncItem> = emptyList()): List<CloudSyncItem>

    /** Must preserve a newer remote revision; a conflict retries the entire merge. */
    suspend fun save(items: List<CloudSyncItem>)

    suspend fun deleteAll()

    suspend fun releaseFetchedAssets() {}
}

data class CloudAvailability(
    val available: Boolean,
    val detail: String,
    val accountId: String? = null,
)

/** Deterministic last-writer merge; a deletion competes with an update using the same revision. */
internal fun mergeCloudItems(
    local: List<CloudSyncItem>,
    remote: List<CloudSyncItem>,
): List<CloudSyncItem> =
    (local + remote)
        .groupBy { it.id }
        .map { (_, versions) ->
            versions.maxWith(
                compareBy<CloudSyncItem> { it.revision }
                    .thenBy { it.origin }
                    .thenBy { it.deleted }
                    .thenBy { it.payload }
                    // Paths are local, but a tied item carrying an asset can repair a missing file.
                    .thenBy { it.assetPath != null },
            )
        }.sortedBy { it.id }

data class CloudSyncState(
    val enabled: Boolean = false,
    val available: Boolean = false,
    val working: Boolean = false,
    val detail: String = "Checking iCloud availability…",
    val syncedItems: Int = 0,
)

/**
 * Explicit opt-in only. Authorization is checked again after network suspensions so a sync cannot
 * import data or send the next batch while the app is locked. The native transport never logs data.
 */
class CloudSyncController(
    private val local: CloudSyncLocalStore,
    private val transport: CloudSyncTransport,
    private val scope: CoroutineScope,
    private val isAuthorized: () -> Boolean,
    private val readEnabled: () -> Boolean,
    private val writeEnabled: (Boolean) -> Unit,
    private val readAccount: () -> String? = { null },
    private val writeAccount: (String?) -> Unit = {},
) {
    private val mutable = MutableStateFlow(CloudSyncState())
    val state: StateFlow<CloudSyncState> = mutable.asStateFlow()
    private var pending: Job? = null
    private var foregroundWaiter: Job? = null
    private val retiring = mutableSetOf<Job>()
    private var operationGeneration = 0L
    private var currentAccount: String? = null
    private var localChangesPending = false

    /** Debounced repository notifications call this; active transfers finish before another merge. */
    fun localDataChanged() {
        if (!state.value.enabled || !state.value.available || !isAuthorized()) return
        if (pending?.isActive == true) localChangesPending = true else syncNow()
    }

    /** A cancelled transfer can still be releasing its own files; wait before an automatic retry. */
    fun onForeground() {
        if (!isAuthorized() || (state.value.working && pending?.isActive == true)) return
        val older = (retiring + listOfNotNull(pending)).filterNot { it.isCompleted }
        val generation = nextGeneration(cancelPending = true)
        val waiter =
            scope.launch {
                older.forEach { it.join() }
                requireAuthorization(generation)
            }
        foregroundWaiter = waiter
        waiter.invokeOnCompletion { cause ->
            if (foregroundWaiter === waiter) foregroundWaiter = null
            if (cause == null && generation == operationGeneration && isAuthorized()) startAvailability(autoSync = true)
        }
    }

    fun refreshAvailability() {
        if (pending?.isActive == true || !isAuthorized()) return
        startAvailability(autoSync = false)
    }

    private fun startAvailability(autoSync: Boolean) {
        val generation = nextGeneration()
        val job =
            scope.launch {
                try {
                    requireAuthorization(generation)
                    val availability = transport.availability()
                    requireAuthorization(generation)
                    currentAccount = availability.accountId
                    val accountChanged = availability.available && readAccount() != availability.accountId
                    if (accountChanged) writeEnabled(false)
                    publish(generation) {
                        it.copy(
                            enabled = !accountChanged && readEnabled(),
                            available = availability.available,
                            working = false,
                            detail = availability.detail,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    publish(
                        generation,
                    ) { it.copy(available = false, working = false, detail = "Could not check iCloud. Try again.") }
                }
            }
        pending = job
        if (autoSync) {
            job.invokeOnCompletion { cause ->
                if (cause == null &&
                    generation == operationGeneration &&
                    canSync()
                ) {
                    syncNow()
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (!isAuthorized() || (enabled && !state.value.available)) return
        val generation = nextGeneration(cancelPending = true)
        pending = null
        localChangesPending = false
        if (enabled) writeAccount(currentAccount)
        writeEnabled(enabled)
        publish(generation) { it.copy(enabled = enabled, working = false) }
        if (enabled) syncNow()
    }

    fun syncNow() {
        if (!state.value.enabled || !state.value.available) return
        if (pending?.isActive == true || !isAuthorized()) return
        val generation = nextGeneration()
        localChangesPending = false
        pending =
            scope.launch {
                val sessionTransport = transport.newSession()
                publish(generation) { it.copy(working = true, detail = "Syncing with your private iCloud…") }
                try {
                    requireAuthorization(generation)
                    val known = local.snapshot()
                    requireAuthorization(generation)
                    val remote = sessionTransport.fetch(known)
                    requireAuthorization(generation)
                    val localItems = local.snapshot()
                    requireAuthorization(generation)
                    val merged = mergeCloudItems(localItems, remote)
                    val remoteById = remote.associateBy { it.id }
                    val localById = localItems.associateBy { it.id }
                    val incoming =
                        merged.filter {
                            !it.sameCloudRevision(localById[it.id]) ||
                                (it.assetPath != null && localById[it.id]?.assetPath == null)
                        }
                    val outgoing = merged.filter { !it.sameCloudRevision(remoteById[it.id]) }
                    // Fetch/merge before upload restores a reinstall without overwriting cloud data.
                    requireAuthorization(generation)
                    if (incoming.isNotEmpty()) local.apply(incoming)
                    requireAuthorization(generation)
                    for (batch in outgoing.chunked(100)) {
                        requireAuthorization(generation)
                        sessionTransport.save(batch)
                        requireAuthorization(generation)
                    }
                    local.rememberSynced(merged)
                    requireAuthorization(generation)
                    publish(generation) {
                        it.copy(
                            working = false,
                            detail = "Up to date",
                            syncedItems =
                                merged.count { item ->
                                    !item.deleted
                                },
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    publish(
                        generation,
                    ) { it.copy(working = false, detail = "Sync incomplete. Your local data is kept; retry Sync now.") }
                } finally {
                    // A stale operation still releases its immutable lease, but cannot touch newer UI state.
                    withContext(NonCancellable) { runCatching { sessionTransport.releaseFetchedAssets() } }
                    publish(generation) { it.copy(working = false) }
                    retryLocalChanges(generation)
                }
            }
    }

    /** UI requires explicit confirmation before invoking this destructive action. */
    fun deleteCloudData() {
        if (pending?.isActive == true || !state.value.available || !isAuthorized()) return
        val generation = nextGeneration()
        writeEnabled(false)
        publish(generation) { it.copy(enabled = false, working = true, detail = "Deleting Orbin data from iCloud…") }
        pending =
            scope.launch {
                try {
                    requireAuthorization(generation)
                    transport.deleteAll()
                    requireAuthorization(generation)
                    publish(
                        generation,
                    ) { it.copy(working = false, detail = "iCloud data deleted. Local data is kept.") }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    publish(
                        generation,
                    ) { it.copy(working = false, detail = "Could not delete iCloud data. Try again.") }
                } finally {
                    publish(generation) { it.copy(working = false) }
                }
            }
    }

    fun onLocked() {
        val generation = nextGeneration(cancelPending = true)
        localChangesPending = false
        publish(generation) { it.copy(working = false) }
    }

    private fun nextGeneration(cancelPending: Boolean = false): Long {
        operationGeneration++
        foregroundWaiter?.cancel()
        foregroundWaiter = null
        if (cancelPending) {
            pending?.takeUnless { it.isCompleted }?.let { previous ->
                retiring.add(previous)
                previous.invokeOnCompletion { retiring.remove(previous) }
                previous.cancel()
            }
        }
        return operationGeneration
    }

    private fun publish(
        generation: Long,
        update: (CloudSyncState) -> CloudSyncState,
    ) {
        if (generation == operationGeneration) mutable.value = update(state.value)
    }

    private suspend fun requireAuthorization(generation: Long) {
        currentCoroutineContext().ensureActive()
        if (generation != operationGeneration) throw CancellationException("Sync operation superseded")
        check(isAuthorized()) { "Unlock Orbin before syncing" }
    }

    private fun retryLocalChanges(generation: Long) {
        if (generation != operationGeneration || !localChangesPending) return
        if (!state.value.enabled || !isAuthorized()) return
        localChangesPending = false
        scope.launch {
            delay(LOCAL_CHANGE_RETRY_MILLIS)
            currentCoroutineContext().ensureActive()
            if (generation == operationGeneration && isAuthorized()) syncNow()
        }
    }

    private fun canSync(): Boolean = state.value.enabled && state.value.available && isAuthorized()
}

private const val LOCAL_CHANGE_RETRY_MILLIS = 1_500L

/** Local file paths differ between devices and are never part of conflict resolution. */
private fun CloudSyncItem.sameCloudRevision(other: CloudSyncItem?): Boolean =
    other != null &&
        id == other.id &&
        revision == other.revision &&
        origin == other.origin &&
        payload == other.payload &&
        deleted == other.deleted

internal fun cloudSyncRows(
    state: CloudSyncState,
    deleteArmed: Boolean,
): List<SettingItem> =
    if (!state.available) {
        listOf(
            SettingItem(
                id = "cloudSyncAvailability",
                label = "iCloud sync",
                value = state.detail,
                kind = SettingKind.INFO,
            ),
        )
    } else {
        listOf(
            SettingItem(
                id = "cloudSyncEnable",
                label = "iCloud sync",
                value = if (state.enabled) "On" else "Off",
                kind = SettingKind.TOGGLE,
                checked = state.enabled,
                hint =
                    if (state.available) {
                        "Optional. Sync portable Orbin data using your private Apple account."
                    } else {
                        state.detail
                    },
            ),
            SettingItem(
                id = "cloudSyncNow",
                label = "Sync now",
                value = state.detail,
                kind = SettingKind.ACTION,
            ),
            SettingItem(
                id = "cloudSyncDelete",
                label = if (deleteArmed) "Confirm delete iCloud data" else "Delete iCloud data",
                value = "",
                hint = "Keeps this device’s data and turns sync off.",
                kind = SettingKind.ACTION,
                destructive = true,
            ),
        )
    }
