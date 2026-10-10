package com.orbin.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** How thread sync is set up and how its last run went, for the settings rows. */
data class ThreadSyncState(
    val folderUrl: String = "",
    val username: String = "",
    val hasPassword: Boolean = false,
    val lastSyncedMillis: Long? = null,
    val error: String? = null,
)

/**
 * Keeps thread progress (read threads, where reading stopped, bookmarks and watched threads) the
 * same across the reader's devices, through a WebDAV folder of their own.
 */
interface ThreadSyncRepository {
    val state: Flow<ThreadSyncState>

    /** Changes only what is given. A new folder starts over with that folder's file. */
    suspend fun setAccount(
        folderUrl: String? = null,
        username: String? = null,
        password: String? = null,
    )

    /** Syncs now if set up, and does nothing otherwise. Server problems land in [state], not thrown. */
    suspend fun sync()
}

/** Sync that is never set up: for previews and tests that don't exercise it. */
object DisabledThreadSync : ThreadSyncRepository {
    override val state: Flow<ThreadSyncState> = flowOf(ThreadSyncState())

    override suspend fun setAccount(
        folderUrl: String?,
        username: String?,
        password: String?,
    ) = Unit

    override suspend fun sync() = Unit
}
