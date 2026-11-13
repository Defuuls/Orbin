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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Leftover Chan, forest, and general-color skins move to Coral; light/dark/AMOLED is kept
 * and the `"color_theme"` key is deleted on the first settings read.
 */
class LegacyThemeMigrationTest {
    private fun CoroutineScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            storage =
                OkioStorage(
                    fileSystem = FileSystem.SYSTEM,
                    serializer = PreferencesSerializer,
                    producePath = {
                        FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
                            "legacy-theme-${Random.nextLong()}.preferences_pb"
                    },
                ),
            scope = this,
        )

    private val chanAndForest =
        setOf(
            "ORBIN",
            "TOMORROW",
            "TOMORROW_NIGHT",
            "AVELLANA",
            "EVITA",
            "HISPAPERRO",
            "HISPASEXY",
            "HISPITA",
            "LAIN",
            "MIKU",
            "MOEOS",
            "MOEPHEUS",
            "PENUMBRA",
            "PENUMBRA_CLEAR",
            "REDCHANIT",
            "ROYAL",
            "SONIC3",
            "VIVIAN",
            "WAROSU",
            "WIN95",
            "YOTSUBA",
            "YOTSUBA_P",
            "YUKKURI",
            "BANANA",
            "APPLE",
        )

    private val appColorThemes = setOf("BLUE", "RED", "WHITE", "BLACK", "PURPLE", "ORANGE", "TEAL")

    @Test
    fun everyChanAndForestSkinMovesToCoralKeepingDarkAmoled() =
        runTest {
            for (value in chanAndForest) {
                val dataStore = backgroundScope.dataStore()
                dataStore.edit {
                    it[LegacyThemeMigration.COLOR_THEME] = value
                    it[stringPreferencesKey("theme_mode")] = "DARK"
                    it[booleanPreferencesKey("amoled")] = true
                }
                val settings = SettingsStore(dataStore).settings.first()
                assertEquals(AppThemeMode.DARK, settings.themeMode, value)
                assertTrue(settings.amoled, value)
                assertFalse(LegacyThemeMigration.COLOR_THEME in dataStore.data.first(), value)
            }
        }

    @Test
    fun everyAppColorThemeIncludingWhiteAndBlackKeepsSystemMode() =
        runTest {
            for (value in appColorThemes) {
                val dataStore = backgroundScope.dataStore()
                dataStore.edit {
                    it[LegacyThemeMigration.COLOR_THEME] = value
                    it[stringPreferencesKey("theme_mode")] = "SYSTEM"
                    it[booleanPreferencesKey("amoled")] = false
                }
                val settings = SettingsStore(dataStore).settings.first()
                assertEquals(AppThemeMode.SYSTEM, settings.themeMode, value)
                assertFalse(settings.amoled, value)
                assertFalse(LegacyThemeMigration.COLOR_THEME in dataStore.data.first(), value)
            }
        }

    @Test
    fun forestAndYotsubaPreserveLightAndDarkAmoledChoices() =
        runTest {
            val cases =
                listOf(
                    "ORBIN" to (AppThemeMode.LIGHT to false),
                    "YOTSUBA" to (AppThemeMode.DARK to true),
                    "ORBIN" to (AppThemeMode.DARK to true),
                    "YOTSUBA" to (AppThemeMode.LIGHT to false),
                )
            for ((skin, choice) in cases) {
                val (mode, amoled) = choice
                val dataStore = backgroundScope.dataStore()
                dataStore.edit {
                    it[LegacyThemeMigration.COLOR_THEME] = skin
                    it[stringPreferencesKey("theme_mode")] = mode.name
                    it[booleanPreferencesKey("amoled")] = amoled
                }
                val settings = SettingsStore(dataStore).settings.first()
                assertEquals(mode, settings.themeMode, "$skin $mode")
                assertEquals(amoled, settings.amoled, "$skin $mode")
                assertFalse(LegacyThemeMigration.COLOR_THEME in dataStore.data.first())
            }
        }

    @Test
    fun anUnknownColorThemeIsRemovedWithoutLosingOtherSettings() =
        runTest {
            val dataStore = backgroundScope.dataStore()
            dataStore.edit {
                it[LegacyThemeMigration.COLOR_THEME] = "FUTURE_COLOR"
                it[stringPreferencesKey("theme_mode")] = "DARK"
                it[booleanPreferencesKey("screen_privacy")] = true
                it[intPreferencesKey("tablet_catalog_columns")] = 8
            }
            val settings = SettingsStore(dataStore).settings.first()
            assertEquals(AppThemeMode.DARK, settings.themeMode)
            assertTrue(settings.screenPrivacyEnabled)
            assertFalse(LegacyThemeMigration.COLOR_THEME in dataStore.data.first())
            assertEquals(8, dataStore.data.first()[intPreferencesKey("tablet_catalog_columns")])
        }

    @Test
    fun aSecondCallWritesNothing() =
        runTest {
            val dataStore = backgroundScope.dataStore()
            dataStore.edit {
                it[LegacyThemeMigration.COLOR_THEME] = "ORBIN"
                it[stringPreferencesKey("theme_mode")] = "LIGHT"
            }
            assertTrue(LegacyThemeMigration.clear(dataStore))
            assertFalse(LegacyThemeMigration.COLOR_THEME in dataStore.data.first())
            val afterFirst = dataStore.data.first().asMap()
            assertFalse(LegacyThemeMigration.clear(dataStore), "a second launch has nothing to do")
            assertEquals(afterFirst, dataStore.data.first().asMap())
        }

    @Test
    fun aFreshStoreIsUnchangedAndEqualsTheDefaults() =
        runTest {
            val dataStore = backgroundScope.dataStore()
            assertFalse(LegacyThemeMigration.clear(dataStore))
            assertEquals(AppSettings.Default, SettingsStore(dataStore).settings.first())
            assertTrue(
                dataStore.data
                    .first()
                    .asMap()
                    .isEmpty(),
            )
        }

    @Test
    fun unrelatedLeftoverKeysAreLeftInPlace() =
        runTest {
            val dataStore = backgroundScope.dataStore()
            dataStore.edit {
                it[LegacyThemeMigration.COLOR_THEME] = "PURPLE"
                it[intPreferencesKey("tablet_catalog_columns")] = 6
                it[stringPreferencesKey("theme_mode")] = "DARK"
            }
            SettingsStore(dataStore).settings.first()
            val left = dataStore.data.first()
            assertFalse(LegacyThemeMigration.COLOR_THEME in left)
            assertEquals(6, left[intPreferencesKey("tablet_catalog_columns")])
            assertEquals("DARK", left[stringPreferencesKey("theme_mode")])
        }

    @Test
    fun historicalListsCoverEveryDocumentedName() {
        val documented = chanAndForest + appColorThemes
        assertEquals(documented, LegacyThemeMigration.HISTORICAL_VALUES)
    }
}
