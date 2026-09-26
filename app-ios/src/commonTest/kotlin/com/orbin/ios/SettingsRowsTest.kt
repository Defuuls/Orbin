package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.uinext.ON_LABEL
import com.orbin.uinext.SettingKind
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRowsTest {
    @Test
    fun theRowsShowTheCurrentSettings() {
        val rows =
            settingsGroups(
                AppSettings(hideNsfwBoards = true, themeMode = AppThemeMode.DARK),
                clearArmed = false,
                imageCacheCleared = false,
            ).flatMap { it.second }.associateBy { it.id }

        assertEquals(ON_LABEL, rows.getValue(SettingIds.HIDE_NSFW).value)
        val theme = rows.getValue(SettingIds.THEME)
        assertEquals(SettingKind.CHOICE, theme.kind)
        assertEquals(listOf("System", "Light", "Dark"), theme.options)
        assertEquals("Dark", theme.value)
        assertEquals(2, theme.selected)
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
}
