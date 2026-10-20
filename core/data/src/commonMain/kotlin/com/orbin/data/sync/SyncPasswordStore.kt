package com.orbin.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Platform storage for the one sync password. Null means absent; an empty password disables sync. */
interface SyncPasswordStore {
    suspend fun read(): String?

    suspend fun write(password: String)
}

/** Android supplies an encrypted DataStore, so its password can stay with the other settings. */
class PreferencesSyncPasswordStore(
    private val preferences: DataStore<Preferences>,
    keyName: String = "sync_password",
) : SyncPasswordStore {
    private val passwordKey = stringPreferencesKey(keyName)

    override suspend fun read(): String? = preferences.data.first()[passwordKey]

    override suspend fun write(password: String) {
        preferences.edit { it[passwordKey] = password }
    }
}

/**
 * Moves an old preference password to secure storage before removing its original copy. Reads
 * and changes share a mutex so a concurrent migration cannot overwrite a newly entered password.
 * Secure storage errors propagate; they are never interpreted as a missing password.
 */
class MigratingSyncPasswordStore(
    private val preferences: DataStore<Preferences>,
    private val secure: SyncPasswordStore,
) : SyncPasswordStore {
    private val mutex = Mutex()

    override suspend fun read(): String? =
        mutex.withLock {
            val stored = secure.read()
            val legacy = preferences.data.first()[SYNC_PASSWORD]
            if (stored != null) {
                if (legacy != null) preferences.edit { it.remove(SYNC_PASSWORD) }
                stored
            } else if (legacy != null) {
                secure.write(legacy)
                preferences.edit { it.remove(SYNC_PASSWORD) }
                legacy
            } else {
                null
            }
        }

    override suspend fun write(password: String) {
        mutex.withLock {
            // Keep an empty secure value as a tombstone: a crash before preference cleanup must
            // never resurrect an old password when the user has cleared it.
            secure.write(password)
            preferences.edit { it.remove(SYNC_PASSWORD) }
        }
    }
}

internal val SYNC_PASSWORD = stringPreferencesKey("sync_password")
