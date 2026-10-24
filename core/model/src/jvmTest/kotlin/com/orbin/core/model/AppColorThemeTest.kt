package com.orbin.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AppColorThemeTest {
    @Test
    fun oldBackupsKeepTheDefaultColor() {
        assertEquals(AppColorTheme.ORBIN, Json.decodeFromString<AppSettings>("{}").colorTheme)
    }

    @Test
    fun colorsRoundTripWithoutChangingBrightnessPreferences() {
        AppColorTheme.entries.forEach { theme ->
            val settings = AppSettings(colorTheme = theme, themeMode = AppThemeMode.DARK, amoled = true)
            assertEquals(settings, Json.decodeFromString<AppSettings>(Json.encodeToString(settings)))
            assertEquals("\"${theme.name}\"", Json.encodeToString(theme))
        }
    }
}
