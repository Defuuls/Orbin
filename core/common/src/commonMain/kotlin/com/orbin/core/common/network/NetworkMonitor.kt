package com.orbin.core.common.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Observes connectivity so the UI can show offline state and the data layer can defer retries. */
interface NetworkMonitor {
    /** Emits true while the device has a validated internet connection. */
    val isOnline: Flow<Boolean>

    /**
     * Emits true while the active connection is metered, and true when the type cannot be
     * determined, so callers fail closed and avoid downloading large previews.
     *
     * Android uses ConnectivityManager NetworkCapabilities (NOT_METERED). iOS uses
     * NWPath.isExpensive.
     */
    val isMetered: Flow<Boolean>

    /**
     * Emits true while Android Data Saver or iOS Low Data Mode is restricting this app.
     * Combined with a Wi-Fi-only setting, this should be treated as Never.
     */
    val isDataSaver: Flow<Boolean>
}

/**
 * A fixed snapshot, used by tests and as a conservative default when no live monitor is wired.
 * An unknown connection is treated as metered.
 */
class StaticNetworkMonitor(
    isOnline: Boolean = true,
    isMetered: Boolean = true,
    isDataSaver: Boolean = false,
) : NetworkMonitor {
    override val isOnline: Flow<Boolean> = MutableStateFlow(isOnline)
    override val isMetered: Flow<Boolean> = MutableStateFlow(isMetered)
    override val isDataSaver: Flow<Boolean> = MutableStateFlow(isDataSaver)
}
