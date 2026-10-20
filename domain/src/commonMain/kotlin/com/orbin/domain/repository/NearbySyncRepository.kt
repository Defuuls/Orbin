package com.orbin.domain.repository

import kotlinx.coroutines.flow.StateFlow

data class NearbyDevice(
    val id: String,
    val name: String,
    val lastSyncedMillis: Long? = null,
    val trusted: Boolean = false,
)

data class NearbySyncState(
    val enabled: Boolean = false,
    val listening: Boolean = false,
    val busy: Boolean = false,
    val pairingCode: String? = null,
    val devices: List<NearbyDevice> = emptyList(),
    val message: String? = null,
)

/** Encrypted direct sync. Both devices must be unlocked in Orbin on the same local network. */
interface NearbySyncRepository {
    val state: StateFlow<NearbySyncState>

    /** This lifecycle signal is separate from the user's persisted enabled setting. */
    fun setActive(active: Boolean)

    suspend fun setEnabled(enabled: Boolean)

    suspend fun createPairingCode()

    suspend fun join(code: String)

    suspend fun syncNow()

    /** Automatic sync requires trust on both devices. Manual sync remains available. */
    suspend fun setTrusted(
        deviceId: String,
        trusted: Boolean,
    )

    suspend fun forget(deviceId: String)
}
