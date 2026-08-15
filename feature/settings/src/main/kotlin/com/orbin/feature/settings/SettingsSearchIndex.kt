package com.orbin.feature.settings

/** A settings sub-screen a search result can deep-link to. Mirrors the app's Settings routes. */
enum class SettingsSection(
    val title: String,
) {
    CONTENT("Content & Feed"),
    NOTIFICATIONS("Notifications"),
    APPEARANCE("Appearance"),
    MEDIA("Media & Playback"),
    PRIVACY("Privacy & Network"),
    ADVANCED("Advanced"),
    STORAGE("Storage & Backup"),
}

/** One searchable setting: its label as shown on its screen, and which screen it lives on. */
data class SettingsSearchEntry(
    val label: String,
    val section: SettingsSection,
) {
    fun matches(query: String): Boolean =
        label.contains(query, ignoreCase = true) || section.title.contains(query, ignoreCase = true)
}

/**
 * Every individual setting across the sub-screens, for the settings search screen to filter.
 * Kept as one flat list next to the hub rather than generated from the screens themselves — the
 * screens' row labels are plain literals with no shared registry to derive this from, and the
 * screens themselves are simple enough that duplication here is easier to keep in sync by hand
 * than a reflection- or annotation-based index would be to maintain.
 */
val settingsSearchIndex =
    listOf(
        SettingsSearchEntry("Personalized home feed", SettingsSection.CONTENT),
        SettingsSearchEntry("Subscriptions", SettingsSection.CONTENT),
        SettingsSearchEntry("Built-in content filter", SettingsSection.CONTENT),
        SettingsSearchEntry("Hidden tags", SettingsSection.CONTENT),
        SettingsSearchEntry("Muted tags", SettingsSection.CONTENT),
        SettingsSearchEntry("Hide NSFW boards", SettingsSection.CONTENT),
        SettingsSearchEntry("Hide text-only threads", SettingsSection.CONTENT),
        SettingsSearchEntry("Refresh feed on return", SettingsSection.CONTENT),
        SettingsSearchEntry("Threads per board", SettingsSection.CONTENT),
        SettingsSearchEntry("Run setup again", SettingsSection.CONTENT),
        SettingsSearchEntry("Thread watch notifications", SettingsSection.NOTIFICATIONS),
        SettingsSearchEntry("Quiet hours start", SettingsSection.NOTIFICATIONS),
        SettingsSearchEntry("Quiet hours end", SettingsSection.NOTIFICATIONS),
        SettingsSearchEntry("Color theme", SettingsSection.APPEARANCE),
        SettingsSearchEntry("App icon", SettingsSection.APPEARANCE),
        SettingsSearchEntry("Theme", SettingsSection.APPEARANCE),
        SettingsSearchEntry("Dynamic color", SettingsSection.APPEARANCE),
        SettingsSearchEntry("AMOLED black", SettingsSection.APPEARANCE),
        SettingsSearchEntry("Open threads as", SettingsSection.APPEARANCE),
        SettingsSearchEntry("Full-screen feed", SettingsSection.APPEARANCE),
        SettingsSearchEntry("Font size", SettingsSection.APPEARANCE),
        SettingsSearchEntry("Thumbnail size", SettingsSection.APPEARANCE),
        SettingsSearchEntry("Show media", SettingsSection.MEDIA),
        SettingsSearchEntry("Autoplay videos", SettingsSection.MEDIA),
        SettingsSearchEntry("Mute by default", SettingsSection.MEDIA),
        SettingsSearchEntry("Fullscreen video", SettingsSection.MEDIA),
        SettingsSearchEntry("Auto-rotate video", SettingsSection.MEDIA),
        SettingsSearchEntry("Media scroll in thread", SettingsSection.MEDIA),
        SettingsSearchEntry("Media scroll in board", SettingsSection.MEDIA),
        SettingsSearchEntry("Autoplay videos in feed", SettingsSection.MEDIA),
        SettingsSearchEntry("Preload images", SettingsSection.MEDIA),
        SettingsSearchEntry("Preload content", SettingsSection.MEDIA),
        SettingsSearchEntry("Preload speed", SettingsSection.MEDIA),
        SettingsSearchEntry("Lock with biometrics", SettingsSection.PRIVACY),
        SettingsSearchEntry("Save recent searches", SettingsSection.PRIVACY),
        SettingsSearchEntry("Internal updater", SettingsSection.PRIVACY),
        SettingsSearchEntry("Check for updates", SettingsSection.PRIVACY),
        SettingsSearchEntry("Clear local activity", SettingsSection.PRIVACY),
        SettingsSearchEntry("HTTPS only", SettingsSection.PRIVACY),
        SettingsSearchEntry("DNS over HTTPS", SettingsSection.PRIVACY),
        SettingsSearchEntry("Custom user agent", SettingsSection.ADVANCED),
        SettingsSearchEntry("Connect timeout", SettingsSection.ADVANCED),
        SettingsSearchEntry("Read timeout", SettingsSection.ADVANCED),
        SettingsSearchEntry("Check certificate revocation", SettingsSection.ADVANCED),
        SettingsSearchEntry("Downloads", SettingsSection.STORAGE),
        SettingsSearchEntry("Saved media folder", SettingsSection.STORAGE),
        SettingsSearchEntry("Download folder structure", SettingsSection.STORAGE),
        SettingsSearchEntry("Image cache limit", SettingsSection.STORAGE),
        SettingsSearchEntry("Export data", SettingsSection.STORAGE),
        SettingsSearchEntry("Import data", SettingsSection.STORAGE),
    )
