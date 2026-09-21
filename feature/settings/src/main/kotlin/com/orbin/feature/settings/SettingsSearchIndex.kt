package com.orbin.feature.settings

/**
 * One searchable setting: the id of its row, its label, and the group heading it sits under.
 *
 * [id] is the same id [buildSettings] gives the row, which is what makes a search result land on
 * the setting itself rather than near it. An earlier index carried a *screen* instead, from when
 * there were seven of them; that is how typing a setting's name could put you in the interface this
 * one replaced.
 */
data class SettingsSearchEntry(
    val id: String,
    val label: String,
    val group: String,
) {
    fun matches(query: String): Boolean =
        label.contains(query, ignoreCase = true) || group.contains(query, ignoreCase = true)
}

/**
 * Every setting the list offers, for the command surface to filter.
 *
 * Kept as a flat list beside the registry rather than derived from it, because the registry needs
 * live [com.orbin.core.model.AppSettings] and a view model to build a row's current value, and the
 * command surface has neither. `SettingsIndexTest` holds the two together: every id here has to
 * exist in the registry, and every row there has to appear here — so a row dropped from the list
 * cannot go on being searchable, which would find you a setting that is no longer there.
 */
val settingsSearchIndex =
    listOf(
        SettingsSearchEntry("personalized", "Personalized feed", GENERAL),
        SettingsSearchEntry("hideNsfw", "Hide NSFW boards", GENERAL),
        SettingsSearchEntry("feedSort", "Feed sort", GENERAL),
        SettingsSearchEntry("threadPresentation", "Open threads", GENERAL),
        SettingsSearchEntry("fullScreenFeed", "Full-screen browsing", GENERAL),
        SettingsSearchEntry("themeMode", "Theme", DISPLAY),
        SettingsSearchEntry("colorTheme", "Color scheme", DISPLAY),
        SettingsSearchEntry("amoled", "True black", DISPLAY),
        SettingsSearchEntry("fontScale", "Text size", DISPLAY),
        SettingsSearchEntry("autoplay", "Autoplay videos", DISPLAY),
        SettingsSearchEntry("mute", "Mute by default", DISPLAY),
        SettingsSearchEntry("preload", "Preload images", DISPLAY),
        SettingsSearchEntry("biometric", "App lock", PRIVACY),
        SettingsSearchEntry("recentSearches", "Save recent searches", PRIVACY),
        SettingsSearchEntry("clearActivity", "Clear local activity", PRIVACY),
        SettingsSearchEntry("downloadFolder", "Downloads folder", PRIVACY),
        SettingsSearchEntry("exportBackup", "Export data", PRIVACY),
        SettingsSearchEntry("importBackup", "Import data", PRIVACY),
        SettingsSearchEntry("internalUpdater", "In-app updates", PRIVACY),
        SettingsSearchEntry("checkUpdates", "Check for updates", PRIVACY),
    )

// The group headings, spelled once. They are the same strings buildSettings groups the rows under.
internal const val GENERAL = "General"
internal const val DISPLAY = "Display & Media"
internal const val PRIVACY = "Privacy & Data"
