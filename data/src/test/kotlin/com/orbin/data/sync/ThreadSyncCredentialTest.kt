package com.orbin.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.orbin.domain.repository.SyncAccountStorageException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThreadSyncCredentialTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `launch sync migrates an inactive accounts password without contacting a server`() =
        runTest {
            val preferences =
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    temporary.root.resolve("inactive.preferences_pb")
                }
            val legacyKey = stringPreferencesKey("sync_password")
            preferences.edit { it[legacyKey] = "legacy" }
            var saved: String? = null
            val secure =
                object : SyncPasswordStore {
                    override suspend fun read(): String? = saved

                    override suspend fun write(password: String) {
                        saved = password
                    }
                }
            // Strict mocks fail on any network or database access: there is no folder configured.
            val sync =
                ThreadSync(
                    mockk(),
                    mockk(),
                    preferences,
                    mockk(),
                    passwords = MigratingSyncPasswordStore(preferences, secure),
                )

            sync.sync()

            assertThat(saved).isEqualTo("legacy")
            assertThat(preferences.data.first()[legacyKey]).isNull()
        }

    @Test
    fun `failed password saves are surfaced and cannot be mistaken for success`() =
        runTest {
            val preferences =
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    temporary.root.resolve("failed.preferences_pb")
                }
            val secure = mockk<SyncPasswordStore>()
            coEvery { secure.read() } returns "old"
            coEvery { secure.write(any()) } throws SyncException("Keychain unavailable")
            val sync = ThreadSync(mockk(), mockk(), preferences, mockk(), passwords = secure)

            val failure = runCatching { sync.setAccount(null, null, "new") }.exceptionOrNull()

            assertThat(failure).isInstanceOf(SyncAccountStorageException::class.java)
            assertThat(sync.state.first().error).isEqualTo("Keychain unavailable")
            assertThat(secure.read()).isEqualTo("old")
        }
}
