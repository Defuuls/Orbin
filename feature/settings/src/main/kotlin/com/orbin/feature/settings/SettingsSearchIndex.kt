package com.orbin.feature.settings

/**
 * One searchable setting: the id of its row, its label, and the group heading it sits under.
 *
 * [id] is the same id [buildSettings] gives the row, which is what makes a search result land on
 * the setting itself rather than near it. The previous index carried a *screen* instead, from when
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
 * Every setting, for the command surface to filter.
 *
 * Kept as a flat list beside the registry rather than derived from it, because the registry needs
 * live [com.orbin.core.model.AppSettings] and a view model to build a row's current value, and the
 * command surface has neither. `NextSettingsIndexTest` holds the two together: every id here has to
 * exist in the registry, and every row there has to appear here.
 */
val settingsSearchIndex =
    listOf(
        SettingsSearchEntry("permanentFilter", "Built-in content filter", CONTENT),
        SettingsSearchEntry("personalized", "Personalized home feed", CONTENT),
        SettingsSearchEntry("hiddenTags", "Hidden tags", CONTENT),
        SettingsSearchEntry("mutedTags", "Muted tags", CONTENT),
        SettingsSearchEntry("hideNsfw", "Hide NSFW boards", CONTENT),
        SettingsSearchEntry("hideTextOnly", "Hide text-only threads", CONTENT),
        SettingsSearchEntry("deepScan", "Deep scan for reply media", CONTENT),
        SettingsSearchEntry("mediaFilter", "Show only", CONTENT),
        SettingsSearchEntry("refreshInterval", "Refresh feed on return", CONTENT),
        SettingsSearchEntry("threadLimit", "Threads per board", CONTENT),
        SettingsSearchEntry("colorTheme", "Color theme", APPEARANCE),
        SettingsSearchEntry("themeMode", "Theme", APPEARANCE),
        SettingsSearchEntry("dynamicColor", "Dynamic color", APPEARANCE),
        SettingsSearchEntry("amoled", "AMOLED black", APPEARANCE),
        SettingsSearchEntry("appIcon", "App icon", APPEARANCE),
        SettingsSearchEntry("threadPresentation", "Open threads as", APPEARANCE),
        SettingsSearchEntry("fullScreenFeed", "Full-screen feed", APPEARANCE),
        SettingsSearchEntry("fontScale", "Font size", APPEARANCE),
        SettingsSearchEntry("thumbnailSize", "Thumbnail size", APPEARANCE),
        SettingsSearchEntry("autoplay", "Autoplay videos", MEDIA),
        SettingsSearchEntry("autoplayFeed", "Autoplay videos in feed", MEDIA),
        SettingsSearchEntry("mute", "Mute by default", MEDIA),
        SettingsSearchEntry("fullscreenVideo", "Fullscreen video", MEDIA),
        SettingsSearchEntry("autoRotate", "Auto-rotate video", MEDIA),
        SettingsSearchEntry("mediaScrollThread", "Media scroll in thread", MEDIA),
        SettingsSearchEntry("mediaScrollBoard", "Media scroll in board", MEDIA),
        SettingsSearchEntry("preload", "Preload images", MEDIA),
        SettingsSearchEntry("preloadOption", "Preload content", MEDIA),
        SettingsSearchEntry("preloadThrottle", "Preload speed", MEDIA),
        SettingsSearchEntry("watchNotifications", "Thread watch notifications", NOTIFICATIONS),
        SettingsSearchEntry("quietStart", "Quiet hours start", NOTIFICATIONS),
        SettingsSearchEntry("quietEnd", "Quiet hours end", NOTIFICATIONS),
        SettingsSearchEntry("httpsOnly", "HTTPS only", PRIVACY),
        SettingsSearchEntry("biometric", "Lock with biometrics", PRIVACY),
        SettingsSearchEntry("recentSearches", "Save recent searches", PRIVACY),
        SettingsSearchEntry("doh", "DNS over HTTPS", PRIVACY),
        SettingsSearchEntry("dnsPrivacy", "DNS privacy", PRIVACY),
        SettingsSearchEntry("ocsp", "Certificate revocation checks", PRIVACY),
        SettingsSearchEntry("userAgent", "Custom user agent", PRIVACY),
        SettingsSearchEntry("connectTimeout", "Connect timeout", PRIVACY),
        SettingsSearchEntry("readTimeout", "Read timeout", PRIVACY),
        SettingsSearchEntry("clearActivity", "Clear local activity", PRIVACY),
        SettingsSearchEntry("crashDetails", "Crash details", PRIVACY),
        SettingsSearchEntry("checkUpdates", "Check for updates", PRIVACY),
        SettingsSearchEntry("cacheLimit", "Image cache limit", STORAGE),
        SettingsSearchEntry("downloadFolder", "Saved media folder", STORAGE),
        SettingsSearchEntry("downloadOrg", "Organize downloads", STORAGE),
        SettingsSearchEntry("exportBackup", "Export data", STORAGE),
        SettingsSearchEntry("importBackup", "Import data", STORAGE),
        SettingsSearchEntry("internalUpdater", "In-app updates", ADVANCED),
        SettingsSearchEntry("runSetup", "Run setup again", ADVANCED),
    )

// The group headings, spelled once. They are the same strings buildSettings groups the rows under.
internal const val CONTENT = "Content & feed"
internal const val APPEARANCE = "Appearance"
internal const val MEDIA = "Media & playback"
internal const val NOTIFICATIONS = "Notifications"
internal const val PRIVACY = "Privacy & network"
internal const val STORAGE = "Storage & backup"
internal const val ADVANCED = "Advanced"
