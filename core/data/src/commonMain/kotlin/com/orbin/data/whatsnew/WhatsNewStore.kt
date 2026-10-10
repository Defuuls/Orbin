package com.orbin.data.whatsnew

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.orbin.domain.repository.WhatsNewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [WhatsNewRepository] in the app's settings file, shared by Android and iOS. */
class WhatsNewStore(
    private val preferences: DataStore<Preferences>,
) : WhatsNewRepository {
    override val lastSeen: Flow<String?> = preferences.data.map { it[LAST_SEEN] }

    override suspend fun markSeen(version: String) {
        preferences.edit { it[LAST_SEEN] = version }
    }

    private companion object {
        val LAST_SEEN = stringPreferencesKey("whats_new_last_seen")
    }
}
