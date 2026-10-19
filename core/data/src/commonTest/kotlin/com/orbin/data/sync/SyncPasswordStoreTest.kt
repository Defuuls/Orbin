package com.orbin.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SyncPasswordStoreTest {
    @Test
    fun migratesTheLegacyPasswordBeforeDeletingIt() =
        runTest {
            val preferences = MemoryPreferences("legacy")
            val secure = MemorySecret()
            secure.beforeWrite = { assertEquals("legacy", preferences.data.first()[SYNC_PASSWORD]) }
            val passwords = MigratingSyncPasswordStore(preferences, secure)

            assertEquals("legacy", passwords.read())
            assertEquals("legacy", secure.password)
            assertNull(preferences.data.first()[SYNC_PASSWORD])
            assertEquals("legacy", passwords.read())
        }

    @Test
    fun failedSecureWriteKeepsTheOriginalAndCanBeRetried() =
        runTest {
            val preferences = MemoryPreferences("legacy")
            val secure = MemorySecret().apply { writeFails = true }
            val passwords = MigratingSyncPasswordStore(preferences, secure)

            assertFailsWith<SyncException> { passwords.read() }
            assertEquals("legacy", preferences.data.first()[SYNC_PASSWORD])
            assertNull(secure.password)

            secure.writeFails = false
            assertEquals("legacy", passwords.read())
            assertNull(preferences.data.first()[SYNC_PASSWORD])
        }

    @Test
    fun unavailableSecureStorageNeverFallsBackToTheLegacyCopy() =
        runTest {
            val preferences = MemoryPreferences("legacy")
            val secure = MemorySecret().apply { readFails = true }

            assertFailsWith<SyncException> { MigratingSyncPasswordStore(preferences, secure).read() }
            assertEquals("legacy", preferences.data.first()[SYNC_PASSWORD])
        }

    @Test
    fun aSecurePasswordWinsOverAnInterruptedMigrationsOldCopy() =
        runTest {
            val preferences = MemoryPreferences("old")
            val secure = MemorySecret().apply { password = "new" }

            assertEquals("new", MigratingSyncPasswordStore(preferences, secure).read())
            assertNull(preferences.data.first()[SYNC_PASSWORD])
        }

    @Test
    fun clearingThePasswordCannotResurrectItAfterInterruptedCleanup() =
        runTest {
            val preferences = MemoryPreferences("legacy").apply { writeFails = true }
            val secure = MemorySecret()
            val passwords = MigratingSyncPasswordStore(preferences, secure)

            assertFailsWith<SyncException> { passwords.write("") }
            assertEquals("", secure.password)
            assertEquals("legacy", preferences.data.first()[SYNC_PASSWORD])

            preferences.writeFails = false
            assertEquals("", passwords.read())
            assertNull(preferences.data.first()[SYNC_PASSWORD])
        }

    @Test
    fun newPasswordsAreOnlyWrittenToSecureStorage() =
        runTest {
            val preferences = MemoryPreferences()
            val secure = MemorySecret()
            val passwords = MigratingSyncPasswordStore(preferences, secure)
            assertNull(passwords.read())

            passwords.write("päss:🔐")

            assertEquals("päss:🔐", passwords.read())
            assertNull(preferences.data.first()[SYNC_PASSWORD])
        }

    @Test
    fun androidKeepsUsingItsEncryptedPreferencesBackend() =
        runTest {
            val preferences = MemoryPreferences()
            val passwords = PreferencesSyncPasswordStore(preferences)
            passwords.write("app-password")
            assertEquals("app-password", passwords.read())
            passwords.write("")
            assertEquals("", passwords.read())
        }

    private class MemorySecret : SyncPasswordStore {
        var password: String? = null
        var readFails = false
        var writeFails = false
        var beforeWrite: suspend () -> Unit = {}

        override suspend fun read(): String? {
            if (readFails) throw SyncException("Keychain unavailable")
            return password
        }

        override suspend fun write(password: String) {
            beforeWrite()
            if (writeFails) throw SyncException("Keychain unavailable")
            this.password = password
        }
    }

    private class MemoryPreferences(
        password: String? = null,
    ) : DataStore<Preferences> {
        override val data =
            MutableStateFlow<Preferences>(
                if (password == null) emptyPreferences() else mutablePreferencesOf(SYNC_PASSWORD to password),
            )
        private val mutex = Mutex()
        var writeFails = false

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock {
                if (writeFails) throw SyncException("Preferences unavailable")
                transform(data.value).also { data.value = it }
            }
    }
}
