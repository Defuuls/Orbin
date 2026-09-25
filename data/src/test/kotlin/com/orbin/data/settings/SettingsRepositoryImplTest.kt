package com.orbin.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.FeedSort
import com.orbin.core.model.ProviderId
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
    fun `empty preferences read exactly as app defaults`() =
        runTest {
            val repository = repository()

            assertThat(repository.settings.first()).isEqualTo(AppSettings.Default)
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
        repository.setDeepMediaScan(true)
        repository.setThemeMode(AppThemeMode.DARK)
        repository.setAmoled(true)
        repository.setFeedSort(FeedSort.TITLE)
        repository.setBiometricLockEnabled(true)
        repository.setSaveRecentSearches(true)
        repository.setActiveProviderId(ProviderId("test-provider"))
        repository.setOnboardingCompleted(true)
    }

    private fun expectedSettings(): AppSettings =
        AppSettings.Default.copy(
            hideNsfwBoards = true,
            deepMediaScan = true,
            themeMode = AppThemeMode.DARK,
            amoled = true,
            feedSort = FeedSort.TITLE,
            biometricLockEnabled = true,
            saveRecentSearches = true,
            activeProviderId = "test-provider",
            onboardingCompleted = true,
        )

    private fun kotlinx.coroutines.test.TestScope.repository(): SettingsRepositoryImpl {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore =
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                context.preferencesDataStoreFile("settings-${UUID.randomUUID()}")
            }
        return SettingsRepositoryImpl(dataStore)
    }
}
