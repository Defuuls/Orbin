package com.orbin.core.common.network

import kotlinx.coroutines.flow.Flow

/** Observes connectivity so the UI can show offline state and the data layer can defer retries. */
interface NetworkMonitor {
    /** Emits true while the device has a validated internet connection. */
    val isOnline: Flow<Boolean>
}
