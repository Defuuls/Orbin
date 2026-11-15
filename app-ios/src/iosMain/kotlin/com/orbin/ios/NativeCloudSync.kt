package com.orbin.ios

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Registered by Swift before the graph is built. No CloudKit call occurs without user opt-in. */
object NativeCloudSync {
    private var handler: ((String, String, (String) -> Unit) -> Unit)? = null

    fun setHandler(block: (operation: String, request: String, completion: (String) -> Unit) -> Unit) {
        handler = block
    }

    internal fun clearHandlerForTesting() {
        handler = null
    }

    internal suspend fun request(
        operation: String,
        items: List<CloudSyncItem> = emptyList(),
        sessionId: String? = null,
    ): NativeCloudReply {
        val native =
            handler ?: return NativeCloudReply(available = false, detail = "iCloud is unavailable in this build.")
        val requestId = NSUUID().UUIDString
        val request = cloudJson.encodeToString(NativeCloudRequest(requestId, sessionId, items))
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                // The Swift task owns CloudKit awaits and staging; cancelling only this continuation
                // would leave those operations running after a lock or a new sync generation.
                runCatching { native("cancel", requestId) {} }
            }
            if (!continuation.isActive) return@suspendCancellableCoroutine
            native(operation, request) { response ->
                if (continuation.isActive) {
                    runCatching { cloudJson.decodeFromString<NativeCloudReply>(response) }
                        .onSuccess { continuation.resume(it) }
                        .onFailure { continuation.resumeWithException(it) }
                }
            }
        }
    }
}

@Serializable
private data class NativeCloudRequest(
    val requestId: String,
    val sessionId: String?,
    val items: List<CloudSyncItem>,
)

@Serializable
internal data class NativeCloudReply(
    val available: Boolean = false,
    val detail: String = "",
    val items: List<CloudSyncItem> = emptyList(),
    val error: String? = null,
    val accountId: String? = null,
)

private val cloudJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

class DeviceCloudSyncTransport private constructor(
    private val sessionId: String?,
) : CloudSyncTransport {
    constructor() : this(null)

    override fun newSession(): CloudSyncTransport = DeviceCloudSyncTransport(NSUUID().UUIDString)

    override suspend fun availability(): CloudAvailability =
        NativeCloudSync.request("availability").let { CloudAvailability(it.available, it.detail, it.accountId) }

    override suspend fun fetch(known: List<CloudSyncItem>): List<CloudSyncItem> =
        NativeCloudSync.request("fetch", known, requireNotNull(sessionId)).checked().items

    override suspend fun save(items: List<CloudSyncItem>) {
        NativeCloudSync.request("save", items, requireNotNull(sessionId)).checked()
    }

    override suspend fun deleteAll() {
        NativeCloudSync.request("deleteAll").checked()
    }

    override suspend fun releaseFetchedAssets() {
        if (sessionId != null) NativeCloudSync.request("cleanup", sessionId = sessionId).checked()
    }

    private fun NativeCloudReply.checked(): NativeCloudReply {
        check(error == null && available) { error ?: "iCloud is unavailable" }
        return this
    }
}
