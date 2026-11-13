package com.orbin.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogModeTest {
    @Test
    fun settingsWithoutACatalogPreferenceDefaultToGrid() {
        assertEquals(CatalogMode.GRID, Json.decodeFromString<AppSettings>("{}").catalogMode)
    }

    @Test
    fun catalogModesRoundTripWithoutChangingBrightnessPreferences() {
        CatalogMode.entries.forEach { mode ->
            val settings = AppSettings(catalogMode = mode, themeMode = AppThemeMode.DARK, amoled = true)
            assertEquals(settings, Json.decodeFromString<AppSettings>(Json.encodeToString(settings)))
            assertEquals("\"${mode.name}\"", Json.encodeToString(mode))
        }
    }
}
