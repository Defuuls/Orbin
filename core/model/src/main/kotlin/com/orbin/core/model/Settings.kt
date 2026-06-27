package com.orbin.core.model

/** Theme preference independent of any UI framework type (mapped to the design-system enum in app). */
enum class AppThemeMode { SYSTEM, LIGHT, DARK }

/**
 * User-configurable application settings, persisted via DataStore. Grouped by the settings screen
 * sections (appearance / media / network) and exposed as one immutable snapshot so the UI observes
 * a single stable object.
 */
data class AppSettings(
    // Appearance
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val fontScale: Float = 1f,
    // Media
    val autoplayVideos: Boolean = false,
    val muteByDefault: Boolean = true,
    val preloadImages: Boolean = true,
    val imageCacheLimitMb: Int = 256,
    // Network / privacy
    val userAgent: String = "",
    val dohEnabled: Boolean = false,
    val httpsOnly: Boolean = true,
) {
    companion object {
        val Default = AppSettings()
    }
}
