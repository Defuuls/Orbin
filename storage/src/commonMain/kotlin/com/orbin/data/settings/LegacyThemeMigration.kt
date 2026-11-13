package com.orbin.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Moves leftover Chan, forest, and general-color skins to Orbin Coral on first launch.
 *
 * Every historical `"color_theme"` value maps to Coral. `theme_mode` and `amoled` are
 * never changed: those are the user's light, dark, or AMOLED choice. The leftover key is
 * then deleted so a later reader cannot revive a skin. Absence of the key is the done state;
 * a second call writes nothing.
 *
 * The live app already paints Coral from `themeMode` + `amoled`. This migration only drops
 * the stale key, and must finish before the first [AppSettings][com.orbin.core.model.AppSettings]
 * emission used by `NextTheme`.
 */
object LegacyThemeMigration {
    val COLOR_THEME = stringPreferencesKey("color_theme")

    /**
     * Historical stored names, from the Chan/forest [ColorTheme] enum and the later
     * [AppColorTheme] general-color enum. Unknown values are treated the same.
     */
    val HISTORICAL_VALUES =
        setOf(
            // ColorTheme (imageboard / Chan skins + forest default)
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
            // AppColorTheme (general colors)
            "BLUE",
            "RED",
            "WHITE",
            "BLACK",
            "PURPLE",
            "ORANGE",
            "TEAL",
        )

    /**
     * Removes `"color_theme"` if it is present. Returns true when a write happened.
     * `theme_mode` and `amoled` are left untouched.
     */
    suspend fun clear(preferences: DataStore<Preferences>): Boolean {
        if (COLOR_THEME !in preferences.data.first()) return false
        preferences.edit { it.remove(COLOR_THEME) }
        return true
    }
}
