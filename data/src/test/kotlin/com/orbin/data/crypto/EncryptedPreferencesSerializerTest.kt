package com.orbin.data.crypto

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.google.common.truth.Truth.assertThat
import com.orbin.data.settings.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

class EncryptedPreferencesSerializerTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private var initialized = false

    // Exercise the real serializer and DataStore recovery boundary without Android Keystore.
    private fun serializer() =
        EncryptedPreferencesSerializer(
            encrypt = { it },
            decrypt = { it },
            wasInitialized = { initialized },
            markInitialized = { initialized = true },
        )

    @Test
    fun `a fresh install keeps normal unlocked onboarding defaults`() {
        val defaults = serializer().defaultValue

        assertThat(defaults.asMap()).isEmpty()
        assertThat(initialized).isFalse()
    }

    @Test
    fun `a missing file after initialization requires authentication`() {
        initialized = true
        val defaults = serializer().defaultValue

        assertThat(defaults[booleanPreferencesKey("biometric_lock")]).isTrue()
        assertThat(defaults[booleanPreferencesKey("onboarding_completed")]).isTrue()
    }

    @Test
    fun `marker lookup failure cannot turn off the lock`() {
        val serializer = EncryptedPreferencesSerializer(wasInitialized = { error("Keystore unavailable") })

        assertThat(serializer.defaultValue[booleanPreferencesKey("biometric_lock")]).isTrue()
    }

    @Test
    fun `deleting the file cannot bypass the real DataStore defaults`() =
        runTest {
            initialized = true
            val file = temporary.root.resolve("deleted-settings")
            val store =
                DataStoreFactory.create(
                    serializer = serializer(),
                    scope = backgroundScope,
                    produceFile = { file },
                )

            val settings = SettingsStore(store).settings.first()

            assertThat(settings.biometricLockEnabled).isTrue()
            assertThat(settings.onboardingCompleted).isTrue()
        }

    @Test
    fun `empty truncated and malformed files recover locked through DataStore`() =
        runTest {
            listOf("", "{", "not ciphertext", "{\"biometric_lock\":{\"t\":\"unknown\",\"v\":false}}")
                .forEachIndexed { index, broken ->
                    val file = temporary.newFile("broken-$index")
                    file.writeText(broken)
                    val store =
                        DataStoreFactory.create(
                            serializer = serializer(),
                            corruptionHandler = ReplaceFileCorruptionHandler { lockedRecoveryPreferences() },
                            scope = backgroundScope,
                            produceFile = { file },
                        )

                    val settings = SettingsStore(store).settings.first()

                    assertThat(settings.biometricLockEnabled).isTrue()
                    assertThat(settings.onboardingCompleted).isTrue()
                    assertThat(initialized).isTrue()
                }
        }

    @Test
    fun `healthy legacy settings are preserved and establish the deletion marker`() =
        runTest {
            val original = lockedRecoveryPreferences()
            val output = ByteArrayOutputStream()
            serializer().writeTo(original, output)
            initialized = false

            val loaded = serializer().readFrom(output.toByteArray().inputStream())

            assertThat(loaded).isEqualTo(original)
            assertThat(initialized).isTrue()
        }

    @Test
    fun `ordinary writes still persist an explicitly disabled lock`() =
        runTest {
            val file = temporary.root.resolve("new-settings")
            val store =
                DataStoreFactory.create(
                    serializer = serializer(),
                    scope = backgroundScope,
                    produceFile = { file },
                )
            store.edit {
                it[booleanPreferencesKey("biometric_lock")] = false
                it[booleanPreferencesKey("onboarding_completed")] = true
            }

            assertThat(SettingsStore(store).settings.first().biometricLockEnabled).isFalse()
            assertThat(initialized).isTrue()
        }
}
