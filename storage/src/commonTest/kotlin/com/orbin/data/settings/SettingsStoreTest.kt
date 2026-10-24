package com.orbin.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.orbin.core.model.AppColorTheme
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shared app settings over a real DataStore file, on the Android host and on iOS. */
class SettingsStoreTest {
    private fun CoroutineScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(scope = this) {
            FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "settings-${Random.nextLong()}.preferences_pb"
        }

    @Test
    fun everyColorSurvivesAStoreWrapperRecreation() =
        runTest {
            val dataStore = backgroundScope.dataStore()
            val store = SettingsStore(dataStore)
            store.setThemeMode(AppThemeMode.DARK)
            store.setAmoled(true)
            AppColorTheme.entries.forEach { theme ->
                store.setColorTheme(theme)
                val restored = SettingsStore(dataStore).settings.first()
                assertEquals(theme, restored.colorTheme)
                assertEquals(AppThemeMode.DARK, restored.themeMode)
                assertTrue(restored.amoled)
            }
        }

    @Test
    fun unknownColorFallsBackWithoutLosingOtherSettings() =
        runTest {
            val dataStore = backgroundScope.dataStore()
            dataStore.edit {
                it[stringPreferencesKey("color_theme")] = "FUTURE_COLOR"
                it[stringPreferencesKey("theme_mode")] = "DARK"
            }
            val restored = SettingsStore(dataStore).settings.first()
            assertEquals(AppColorTheme.ORBIN, restored.colorTheme)
            assertEquals(AppThemeMode.DARK, restored.themeMode)
        }

    @Test
    fun aNewStoreReadsAsTheDefaults() =
        runTest {
            assertEquals(AppSettings.Default, SettingsStore(backgroundScope.dataStore()).settings.first())
        }

    @Test
    fun whatIsSetIsReadBack() =
        runTest {
            val store = SettingsStore(backgroundScope.dataStore())

            store.setHideNsfwBoards(true)
            store.setThemeMode(AppThemeMode.DARK)
            store.setAmoled(true)

            val settings = store.settings.first()
            assertTrue(settings.hideNsfwBoards)
            assertEquals(AppThemeMode.DARK, settings.themeMode)
            assertTrue(settings.amoled)
        }

    @Test
    fun itReadsTheKeysAndroidHasAlwaysWritten() =
        runTest {
            val dataStore = backgroundScope.dataStore()
            dataStore.edit {
                it[booleanPreferencesKey("hide_nsfw_boards")] = true
                it[stringPreferencesKey("theme_mode")] = "LIGHT"
            }

            val settings = SettingsStore(dataStore).settings.first()
            assertTrue(settings.hideNsfwBoards)
            assertEquals(AppThemeMode.LIGHT, settings.themeMode)
            assertEquals(AppColorTheme.ORBIN, settings.colorTheme)
        }
}
