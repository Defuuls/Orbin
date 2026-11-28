package com.orbin.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.AppInterface
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.FormFactor
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SharpPreviewsMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

private const val ROBOLECTRIC_SDK = 35

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class SettingsRepositoryImplTest {
    @Test
    fun `fresh preferences hide NSFW while retaining all other app defaults`() =
        runTest {
            val repository = repository()

            assertThat(repository.settings.first()).isEqualTo(AppSettings.Default.copy(hideNsfwBoards = true))
        }

    @Test
    fun `legacy implicit and explicit NSFW preferences stay unchanged`() =
        runTest {
            val implicit =
                repository { dataStore ->
                    dataStore.edit { it[stringPreferencesKey("theme_mode")] = "DARK" }
                }
            assertThat(implicit.settings.first().hideNsfwBoards).isFalse()
            val explicit =
                repository { dataStore ->
                    dataStore.edit { it[booleanPreferencesKey("hide_nsfw_boards")] = false }
                }
            assertThat(explicit.settings.first().hideNsfwBoards).isFalse()
        }

    @Test
    fun `every app setting setter persists and reads back`() =
        runTest {
            val repository = repository()

            applyAllSettings(repository)

            assertThat(repository.settings.first()).isEqualTo(expectedSettings())
        }

    private suspend fun applyAllSettings(repository: SettingsRepositoryImpl) {
        repository.setHideNsfwBoards(true)
        repository.setCoverViolentMedia(false)
        repository.setDeepMediaScan(true)
        repository.setThemeMode(AppThemeMode.DARK)
        repository.setAmoled(true)
        repository.setAppInterface(AppInterface.PULSE)
        repository.setBiometricLockEnabled(true)
        repository.setActiveProviderId(ProviderId("test-provider"))
        repository.setOnboardingCompleted(true)
        repository.setFeedColumns(FormFactor.FOLDABLE, 3)
        repository.setFeedColumns(FormFactor.TABLET, 4)
        repository.setSharpPreviews(SharpPreviewsMode.ALWAYS)
    }

    private fun expectedSettings(): AppSettings =
        AppSettings.Default.copy(
            hideNsfwBoards = true,
            coverViolentMedia = false,
            deepMediaScan = true,
            themeMode = AppThemeMode.DARK,
            amoled = true,
            appInterface = AppInterface.PULSE,
            biometricLockEnabled = true,
            activeProviderId = "test-provider",
            onboardingCompleted = true,
            unfoldedFeedColumns = 3,
            tabletFeedColumns = 4,
            sharpPreviews = SharpPreviewsMode.ALWAYS,
        )

    private suspend fun kotlinx.coroutines.test.TestScope.repository(
        prepare: suspend (DataStore<Preferences>) -> Unit = {},
    ): SettingsRepositoryImpl {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore =
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                context.preferencesDataStoreFile("settings-${UUID.randomUUID()}")
            }
        prepare(dataStore)
        return SettingsRepositoryImpl(SettingsStore(dataStore), BoardPreferencesStore(dataStore))
    }
}
