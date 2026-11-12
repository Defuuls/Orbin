package com.orbin.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.CatalogMode
import com.orbin.core.model.DohProvider
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.PlaybackSettings
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
        PreferenceDataStoreFactory.create(
            storage =
                OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = PreferencesSerializer,
                    producePath = {
                        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "settings-${Random.nextLong()}.preferences_pb"
                    },
                ),
            scope = this,
        )

    @Test
    fun scopedPlaybackPersistsAndResetResumesInheritance() =
        runTest {
            val dataStore = backgroundScope.dataStore()
            val store = SettingsStore(dataStore)
            val site = PlaybackScope("4chan")
            val board = PlaybackScope("4chan", "gif")
            store.setPlaybackSettings(site, PlaybackSettings(autoplay = false))
            store.setPlaybackSettings(board, PlaybackSettings(startMuted = false))
            val restored = SettingsStore(dataStore).settings.first().playbackPreferences
            assertEquals(false, restored.forScope(board).startMuted)
            assertEquals(false, restored.forScope(PlaybackScope("4chan", "g")).autoplay)
            assertEquals(true, restored.forScope(PlaybackScope("bbwchan", "gif")).autoplay)
            store.setPlaybackSettings(board, null)
            assertEquals(
                false,
                SettingsStore(dataStore)
                    .settings
                    .first()
                    .playbackPreferences
                    .forScope(board)
                    .autoplay,
            )
            assertEquals(
                true,
                SettingsStore(dataStore)
                    .settings
                    .first()
                    .playbackPreferences
                    .forScope(board)
                    .startMuted,
            )
        }

    @Test
    fun malformedPlaybackDoesNotPreventOtherSettingsFromLoading() =
        runTest {
            val dataStore = backgroundScope.dataStore()
            dataStore.edit {
                it[stringPreferencesKey("playback_preferences")] = "invalid"
                it[booleanPreferencesKey("screen_privacy")] = true
            }
            val restored = SettingsStore(dataStore).settings.first()
            assertEquals(AppSettings.Default.playbackPreferences, restored.playbackPreferences)
            assertTrue(restored.screenPrivacyEnabled)
        }

    @Test
    fun everyCatalogModeSurvivesAStoreWrapperRecreation() =
        runTest {
            val dataStore = backgroundScope.dataStore()
            val store = SettingsStore(dataStore)
            AppThemeMode.entries.forEach { mode ->
                store.setThemeMode(mode)
                listOf(true, false).forEach { amoled ->
                    store.setAmoled(amoled)
                    CatalogMode.entries.forEach { catalogMode ->
                        store.setCatalogMode(catalogMode)
                        val restored = SettingsStore(dataStore).settings.first()
                        assertEquals(catalogMode, restored.catalogMode)
                        assertEquals(mode, restored.themeMode)
                        assertEquals(amoled, restored.amoled)
                    }
                }
            }
        }

    @Test
    fun legacyColorAndColumnSettingsAreIgnoredWithoutLosingOtherSettings() =
        runTest {
            val dataStore = backgroundScope.dataStore()
            dataStore.edit {
                it[stringPreferencesKey("color_theme")] = "FUTURE_COLOR"
                it[intPreferencesKey("tablet_catalog_columns")] = 8
                it[stringPreferencesKey("theme_mode")] = "DARK"
            }
            val restored = SettingsStore(dataStore).settings.first()
            assertEquals(CatalogMode.LIST, restored.catalogMode)
            assertEquals(AppThemeMode.DARK, restored.themeMode)
        }

    @Test
    fun unknownCatalogModeFallsBackToListWithoutLosingOtherSettings() =
        runTest {
            val dataStore = backgroundScope.dataStore()
            dataStore.edit {
                it[stringPreferencesKey("catalog_mode")] = "FUTURE_MODE"
                it[booleanPreferencesKey("screen_privacy")] = true
            }

            val restored = SettingsStore(dataStore).settings.first()

            assertEquals(CatalogMode.LIST, restored.catalogMode)
            assertTrue(restored.screenPrivacyEnabled)
        }

    @Test
    fun savedCatalogModeTakesPrecedenceOverIgnoredLegacyValues() =
        runTest {
            val dataStore = backgroundScope.dataStore()
            dataStore.edit {
                it[stringPreferencesKey("color_theme")] = "PURPLE"
                it[intPreferencesKey("tablet_catalog_columns")] = -1
            }
            SettingsStore(dataStore).setCatalogMode(CatalogMode.GRID)

            assertEquals(CatalogMode.GRID, SettingsStore(dataStore).settings.first().catalogMode)
        }

    @Test
    fun aNewStoreReadsAsTheDefaults() =
        runTest {
            assertEquals(AppSettings.Default, SettingsStore(backgroundScope.dataStore()).settings.first())
        }

    @Test
    fun existingStoresOpenThreadVideosUntilCommentsAreEnabled() =
        runTest {
            val dataStore = backgroundScope.dataStore()
            dataStore.edit { it[stringPreferencesKey("theme_mode")] = "DARK" }

            val restored = SettingsStore(dataStore).settings.first()

            assertEquals(false, restored.openThreadComments)
            assertEquals(AppThemeMode.DARK, restored.themeMode)
        }

    @Test
    fun chatButtonPreferencePersistsAcrossStoreWrapperRecreation() =
        runTest {
            val dataStore = backgroundScope.dataStore()
            val store = SettingsStore(dataStore)
            store.setHideNsfwBoards(true)
            store.setOpenThreadComments(true)

            val restored = SettingsStore(dataStore)
            assertEquals(true, restored.settings.first().openThreadComments)
            restored.setOpenThreadComments(false)

            val reset = SettingsStore(dataStore).settings.first()
            assertEquals(false, reset.openThreadComments)
            assertTrue(reset.hideNsfwBoards)
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
            assertEquals(CatalogMode.LIST, settings.catalogMode)
        }

    @Test
    fun privacyAndSecuritySettingsPersistAndReadBack() =
        runTest {
            val store = SettingsStore(backgroundScope.dataStore())

            store.setScreenPrivacyEnabled(true)
            store.setDohProvider(DohProvider.NEXTDNS)
            store.setStrictDohEnabled(true)
            store.setSaveSearchHistory(true)
            store.setSaveBrowsingHistory(false)

            val settings = store.settings.first()
            assertTrue(settings.screenPrivacyEnabled)
            assertEquals(DohProvider.NEXTDNS, settings.dohProvider)
            assertTrue(settings.strictDohEnabled)
            assertEquals(true, settings.saveSearchHistory)
            assertEquals(false, settings.saveBrowsingHistory)
        }
}
