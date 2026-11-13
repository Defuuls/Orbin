package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.CatalogMode
import com.orbin.uinext.OFF_LABEL
import com.orbin.uinext.ON_LABEL
import com.orbin.uinext.SettingKind
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsRowsTest {
    @Test
    fun sectionsKeepEverySettingReachableExactlyOnce() {
        val groups = settingsGroups(AppSettings.Default, false, false)
        assertEquals(listOf("Appearance", "Privacy and safety", "Data and storage", "About"), groups.map { it.first })
        assertTrue(groups.all { it.second.isNotEmpty() })
        val ids = groups.flatMap { it.second }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(17, ids.size)
    }

    @Test
    fun everyCatalogViewShowsItsSavedSelection() {
        CatalogMode.entries.forEach { mode ->
            val row =
                settingsGroups(AppSettings(catalogMode = mode), false, false)
                    .flatMap { it.second }
                    .single { it.id == SettingIds.CATALOG_MODE }
            assertEquals(SettingKind.CHOICE, row.kind)
            assertEquals(listOf("List", "Grid"), row.options)
            assertEquals(if (mode == CatalogMode.LIST) "List" else "Grid", row.value)
            assertEquals(mode.ordinal, row.selected)
        }
    }

    @Test
    fun catalogViewWritesThroughTheSharedRepositoryAndSurvivesReaderRecreation() =
        runTest {
            val repository = FakeSettings()
            val reader = ReaderSettings(repository, FakeHistory(), backgroundScope)
            runCurrent()
            reader.setCatalogMode(CatalogMode.GRID).join()
            runCurrent()
            assertEquals(CatalogMode.GRID, reader.current.value.catalogMode)
            assertEquals(CatalogMode.GRID, repository.settings.value.catalogMode)
            val reopened = ReaderSettings(repository, FakeHistory(), backgroundScope)
            runCurrent()
            assertEquals(CatalogMode.GRID, reopened.current.value.catalogMode)
            assertEquals(AppThemeMode.SYSTEM, reopened.current.value.themeMode)
        }

    @Test
    fun theRowsShowTheCurrentSettings() {
        val rows =
            settingsGroups(
                AppSettings(hideNsfwBoards = true, themeMode = AppThemeMode.DARK),
                clearArmed = false,
                imageCacheCleared = false,
            ).flatMap { it.second }.associateBy { it.id }

        assertEquals(ON_LABEL, rows.getValue(SettingIds.HIDE_NSFW).value)
        // On by default, as on Android.
        assertEquals(ON_LABEL, rows.getValue(SettingIds.COVER_VIOLENT).value)
        val theme = rows.getValue(SettingIds.THEME)
        assertEquals(SettingKind.CHOICE, theme.kind)
        assertEquals(listOf("System", "Light", "Dark"), theme.options)
        assertEquals("Dark", theme.value)
        assertEquals(2, theme.selected)
    }

    @Test
    fun thePrivacyPolicyAndContactAreReachableFromSettings() {
        val rows =
            settingsGroups(AppSettings(), clearArmed = false, imageCacheCleared = false)
                .flatMap { it.second }
                .associateBy { it.id }

        assertEquals(SettingKind.ACTION, rows.getValue(SettingIds.PRIVACY).kind)
        assertEquals(SettingKind.ACTION, rows.getValue(SettingIds.CONTACT).kind)
        assertTrue(PRIVACY_POLICY_URL.startsWith("https://"))
        assertTrue(CONTACT_URL.startsWith("https://"))
    }

    @Test
    fun appearanceHasOnlyBrightnessAmoledAndDeviceWideCatalogView() {
        val appearance = settingsGroups(AppSettings(), false, false).single { it.first == "Appearance" }.second
        assertEquals(listOf(SettingIds.THEME, SettingIds.AMOLED, SettingIds.CATALOG_MODE), appearance.map { it.id })
        assertEquals(listOf("Theme", "AMOLED black", "Catalog view"), appearance.map { it.label })
        val catalog = appearance.single { it.id == SettingIds.CATALOG_MODE }
        assertEquals(listOf("List", "Grid"), catalog.options)
        assertEquals("Grid", catalog.value)
        assertEquals(1, catalog.selected)
    }

    @Test
    fun theViolentMediaCoverCanBeTurnedOff() {
        val row =
            settingsGroups(AppSettings(coverViolentMedia = false), clearArmed = false, imageCacheCleared = false)
                .flatMap { it.second }
                .single { it.id == SettingIds.COVER_VIOLENT }
        assertEquals(OFF_LABEL, row.value)
        assertEquals(SettingKind.TOGGLE, row.kind)
        assertEquals(false, row.checked)
    }

    @Test
    fun clearingActivityAsksForASecondTap() {
        fun clearRow(armed: Boolean) =
            settingsGroups(AppSettings.Default, clearArmed = armed, imageCacheCleared = false)
                .flatMap { it.second }
                .single { it.id == SettingIds.CLEAR_ACTIVITY }
                .value

        assertEquals("Delete", clearRow(armed = false))
        assertEquals("Tap again to delete", clearRow(armed = true))
    }

    @Test
    fun theVersionRowNamesTheAndroidReleaseAndTheTestFlightBuild() {
        val rows =
            settingsGroups(
                AppSettings.Default,
                clearArmed = false,
                imageCacheCleared = false,
                version = AppVersion(name = "158-Yuzu", build = "176.202609262351"),
            ).flatMap { it.second }.associateBy { it.id }

        val version = rows.getValue(SettingIds.VERSION)
        assertEquals(SettingKind.INFO, version.kind)
        assertEquals("158-Yuzu", version.value)
        assertEquals("Build 176.202609262351", version.hint)
    }

    @Test
    fun aBuildWithNoVersionShowsNoVersionRow() {
        val ids =
            settingsGroups(AppSettings.Default, clearArmed = false, imageCacheCleared = false)
                .flatMap { it.second }
                .map { it.id }

        assertTrue(SettingIds.VERSION !in ids)
    }

    @Test
    fun destructiveRowsUseTheDestructiveFlag() {
        val rows =
            settingsGroups(AppSettings.Default, clearArmed = false, imageCacheCleared = false)
                .flatMap { it.second }
                .associateBy { it.id }
        assertTrue(rows.getValue(SettingIds.CLEAR_ACTIVITY).destructive)
        assertTrue(rows.getValue(SettingIds.CLEAR_IMAGE_CACHE).destructive)
        assertTrue(!rows.getValue(SettingIds.EXPORT_BACKUP).destructive)
    }

    @Test
    fun privacySettingsIncludeShieldDohAndHistoryToggles() {
        val rows =
            settingsGroups(
                AppSettings(
                    screenPrivacyEnabled = true,
                    strictDohEnabled = true,
                    saveSearchHistory = false,
                    saveBrowsingHistory = false,
                ),
                clearArmed = false,
                imageCacheCleared = false,
            ).flatMap { it.second }.associateBy { it.id }

        assertEquals(ON_LABEL, rows.getValue(SettingIds.SCREEN_PRIVACY).value)
        assertEquals(true, rows.getValue(SettingIds.SCREEN_PRIVACY).checked)
        assertEquals(SettingKind.CHOICE, rows.getValue(SettingIds.DOH_PROVIDER).kind)
        assertEquals(ON_LABEL, rows.getValue(SettingIds.STRICT_DOH).value)
        assertEquals(OFF_LABEL, rows.getValue(SettingIds.SAVE_SEARCH_HISTORY).value)
        assertEquals(false, rows.getValue(SettingIds.SAVE_SEARCH_HISTORY).checked)
        assertEquals(OFF_LABEL, rows.getValue(SettingIds.SAVE_BROWSING_HISTORY).value)
        assertEquals(false, rows.getValue(SettingIds.SAVE_BROWSING_HISTORY).checked)
    }
}
