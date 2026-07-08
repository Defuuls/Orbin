package com.orbin.core.model

/** Theme preference independent of any UI framework type (mapped to the design-system enum in app). */
enum class AppThemeMode { SYSTEM, LIGHT, DARK }

private const val FEED_LIMIT_SIX = 6
private const val FEED_LIMIT_TWELVE = 12
private const val FEED_LIMIT_EIGHTEEN = 18
private const val THUMBNAIL_SIZE_COMPACT_DP = 80
private const val THUMBNAIL_SIZE_MEDIUM_DP = 96
private const val THUMBNAIL_SIZE_LARGE_DP = 120
private const val THUMBNAIL_SIZE_FILL_DP = 240

enum class FeedThreadLimit(
    val count: Int?,
    val label: String,
) {
    SIX(FEED_LIMIT_SIX, "6"),
    TWELVE(FEED_LIMIT_TWELVE, "12"),
    EIGHTEEN(FEED_LIMIT_EIGHTEEN, "18"),
    ALL(null, "All"),
}

enum class DohProvider(
    val label: String,
) {
    CLOUDFLARE("Cloudflare"),
    OPENDNS("OpenDNS"),
    NEXTDNS("NextDNS"),
}

enum class ThumbnailSize(
    val label: String,
    val sizeDp: Int,
) {
    COMPACT("Compact", THUMBNAIL_SIZE_COMPACT_DP),
    MEDIUM("Medium", THUMBNAIL_SIZE_MEDIUM_DP),
    LARGE("Large", THUMBNAIL_SIZE_LARGE_DP),

    /**
     * As large as the layout allows - a single column of full-width thumbnails in the thread
     * grid view. [sizeDp] is only a sane fallback for layouts (like the subscribed feed) that
     * size thumbnails as a fixed square rather than filling the available width.
     */
    FILL("Fill", THUMBNAIL_SIZE_FILL_DP),
}

/**
 * User-configurable application settings, persisted via DataStore. Grouped by the settings screen
 * sections (appearance / media / network) and exposed as one immutable snapshot so the UI observes
 * a single stable object.
 */
data class AppSettings(
    // Home / content
    val personalizedHomeFeed: Boolean = true,
    val hiddenTags: String = "",
    val mutedTags: String = "",
    val hideNsfwBoards: Boolean = false,
    val hideTextOnlyThreads: Boolean = false,
    // Appearance
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val fontScale: Float = 1f,
    val thumbnailSize: ThumbnailSize = ThumbnailSize.MEDIUM,
    // Media
    val autoplayVideos: Boolean = false,
    val muteByDefault: Boolean = true,
    val preloadImages: Boolean = true,
    val preloadOption: PreloadOption = PreloadOption.IMAGES,
    val preloadThrottleMode: PreloadThrottleMode = PreloadThrottleMode.MODERATE,
    val imageCacheLimitMb: Int = 256,
    val feedThreadLimit: FeedThreadLimit = FeedThreadLimit.TWELVE,
    val downloadFolderUri: String = "",
    // Network / privacy
    val userAgent: String = "",
    val dohEnabled: Boolean = false,
    val dohProvider: DohProvider = DohProvider.CLOUDFLARE,
    val httpsOnly: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val saveRecentSearches: Boolean = false,
    // Providers - empty activeProviderId means "use the registry default"
    val activeProviderId: String = "",
    // First-run
    val onboardingCompleted: Boolean = false,
) {
    companion object {
        val Default = AppSettings()
    }
}

enum class PreloadThrottleMode(
    val label: String,
) {
    CONSERVATIVE("Conservative (1 at a time)"),
    MODERATE("Moderate (2 at a time)"),
    AGGRESSIVE("Aggressive (3 at a time)"),
    ;
}

fun AppSettings.hiddenTagTokens(): Set<String> = parseFilterTokens(hiddenTags)

fun AppSettings.mutedTagTokens(): Set<String> = parseFilterTokens(mutedTags)

private fun parseFilterTokens(raw: String): Set<String> =
    raw
        .split(',', '\n')
        .map { token -> token.trim().removePrefix("#").lowercase() }
        .filter { token -> token.isNotBlank() }
        .toSet()
