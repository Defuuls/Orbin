package com.orbin.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** Removed sync features must not leave a login or activity copy behind in preferences. */
class LegacySyncCleanupTest {
    private fun CoroutineScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(scope = this) {
            FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "legacy-sync-${Random.nextLong()}.preferences_pb"
        }

    @Test
    fun removesEverySyncEntryAndKeepsEverythingElse() =
        runTest {
            val store = backgroundScope.dataStore()
            val kept = stringPreferencesKey("theme_mode")
            store.edit {
                it[stringPreferencesKey("sync_folder_url")] = "https://dav.example/orbin/"
                it[stringPreferencesKey("sync_username")] = "reader"
                it[stringPreferencesKey("sync_password")] = "secret"
                it[stringPreferencesKey("sync_agreed_state")] = "{}"
                it[longPreferencesKey("sync_last_synced")] = 1L
                it[stringPreferencesKey("sync_last_error")] = "offline"
                it[stringPreferencesKey("nearby_sync_identity")] = "identity"
                it[stringPreferencesKey("nearby_agreed_pair1")] = "{}"
                it[kept] = "dark"
            }

            assertEquals(8, LegacySyncCleanup.clear(store))

            val left = store.data.first().asMap()
            assertEquals(setOf<Preferences.Key<*>>(kept), left.keys)
            assertEquals(0, LegacySyncCleanup.clear(store), "a second launch has nothing to do")
        }
}
