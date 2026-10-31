package com.orbin.network

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Where the strict encrypted DNS choice is kept.
 *
 * Not in the DataStore settings: those are built from the shared graph, which is built from the
 * HTTP client this setting configures. A small private preferences file can be read before any of
 * that exists, on the first lookup.
 */
interface StrictDnsStore {
    fun load(): Boolean

    fun save(enabled: Boolean)

    /** Keeps the choice for the life of the object only; for tests. */
    class InMemory(
        private var enabled: Boolean = false,
    ) : StrictDnsStore {
        override fun load(): Boolean = enabled

        override fun save(enabled: Boolean) {
            this.enabled = enabled
        }
    }
}

/** [StrictDnsStore] in a private preferences file. */
class SharedPreferencesStrictDnsStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : StrictDnsStore {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun load(): Boolean = prefs.getBoolean(KEY_STRICT, false)

        override fun save(enabled: Boolean) {
            prefs.edit { putBoolean(KEY_STRICT, enabled) }
        }

        private companion object {
            const val PREFS_NAME = "orbin_dns"
            const val KEY_STRICT = "strict_encrypted_dns"
        }
    }
