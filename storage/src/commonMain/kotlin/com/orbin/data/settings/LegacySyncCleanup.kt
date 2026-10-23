package com.orbin.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

/**
 * Removes what WebDAV sync and nearby device sync left in preferences after both were removed:
 * the WebDAV folder, username and password (Android kept the password here; iOS kept a copy here
 * before moving it to the Keychain), the last sync state, the nearby pairing identity and the
 * per-pairing merge bases. Nothing reads these any more, so a login must not linger on the device.
 *
 * Writes only when something is left, so later launches cost one read.
 */
object LegacySyncCleanup {
    private val exactKeys =
        setOf(
            "sync_folder_url",
            "sync_username",
            "sync_password",
            "sync_agreed_state",
            "sync_last_synced",
            "sync_last_error",
            "nearby_sync_identity",
        )
    private const val NEARBY_BASE_PREFIX = "nearby_agreed_"

    /** Returns how many entries were removed. */
    suspend fun clear(preferences: DataStore<Preferences>): Int {
        val stale =
            preferences.data
                .first()
                .asMap()
                .keys
                .filter { it.isLegacySync() }
        if (stale.isEmpty()) return 0
        preferences.edit { prefs -> stale.forEach { prefs.remove(it) } }
        return stale.size
    }

    private fun Preferences.Key<*>.isLegacySync(): Boolean = name in exactKeys || name.startsWith(NEARBY_BASE_PREFIX)
}
