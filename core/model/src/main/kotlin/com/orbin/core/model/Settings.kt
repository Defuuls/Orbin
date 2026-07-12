package com.orbin.core.model

/** Theme preference independent of any UI framework type (mapped to the design-system enum in app). */
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Color theme variant for the app. Beyond the built-in Orbin/Tomorrow schemes, this includes the
 * palette skins ported from 8chan; the design-system layer maps each entry to a Material scheme.
 */
enum class ColorTheme(
    val label: String,
) {
    ORBIN("Orbin"),
    TOMORROW("Tomorrow"),
    TOMORROW_NIGHT("Tomorrow Night"),
    AVELLANA("Avellana"),
    EVITA("Evita"),
    HISPAPERRO("Hispaperro"),
    HISPASEXY("Hispasexy"),
    HISPITA("Hispita"),
    LAIN("Lain"),
    MIKU("Miku"),
    MOEOS("MoeOS8"),
    MOEPHEUS("Moephus"),
    PENUMBRA("Penumbra"),
    PENUMBRA_CLEAR("Penumbra (Clear)"),
    REDCHANIT("Redchanit"),
    ROYAL("Royal"),
    SONIC3("Sonic 3 & Knuckles"),
    VIVIAN("Vivian"),
    WAROSU("Warosu"),
    WIN95("Windows 95"),
    YOTSUBA("Yotsuba"),
    YOTSUBA_P("Yotsuba P"),
    YUKKURI("Yukkuri"),
}

/** App icon variant for home screen. */
enum class AppIconVariant(
    val label: String,
) {
    DEFAULT("Default"),
    MINIMALIST("Minimalist"),
    GRADIENT("Gradient"),
    NEON("Neon"),
    RETRO("Retro"),
}

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
    val personalizedHomeFeed: Boolean = true,
    val hiddenTags: String = "",
    val mutedTags: String = "",
    val hideNsfwBoards: Boolean = false,
    val hideTextOnlyThreads: Boolean = false,
    /** Reload the subscribed feed when returning to it, e.g. after backing out of a thread. */
    val refreshFeedOnReturn: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val colorTheme: ColorTheme = ColorTheme.ORBIN,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val fontScale: Float = 1f,
    val appIconVariant: AppIconVariant = AppIconVariant.DEFAULT,
    val fullScreenFeedChrome: Boolean = false,
    val thumbnailSize: ThumbnailSize = ThumbnailSize.MEDIUM,
    val autoplayVideos: Boolean = false,
    val muteByDefault: Boolean = true,
    /** Play videos in an immersive full-screen presentation (hide system bars and app chrome). */
    val fullscreenVideoPlayback: Boolean = false,
    /** Rotate the screen to landscape automatically when a landscape video starts playing. */
    val autoRotateVideoFullscreen: Boolean = false,
    val preloadImages: Boolean = true,
    val preloadOption: PreloadOption = PreloadOption.IMAGES,
    val preloadThrottleMode: PreloadThrottleMode = PreloadThrottleMode.MODERATE,
    val imageCacheLimitMb: Int = 256,
    val feedThreadLimit: FeedThreadLimit = FeedThreadLimit.TWELVE,
    val downloadFolderUri: String = "",
    val userAgent: String = "",
    val dohEnabled: Boolean = false,
    val dohProvider: DohProvider = DohProvider.CLOUDFLARE,
    val httpsOnly: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val saveRecentSearches: Boolean = false,
    val internalUpdaterEnabled: Boolean = true,
    val activeProviderId: String = "",
    val onboardingCompleted: Boolean = false,
) {
    companion object {
        val Default = AppSettings()
    }
}

/**
 * How fast media preloading is allowed to hit the CDN. The throttled modes trade speed for
 * safety against server-side rate limits; [UNLIMITED] removes all client-side pacing (no
 * delays, no per-minute cap) and preloads many files in parallel for uninterrupted browsing.
 */
enum class PreloadThrottleMode(
    val label: String,
) {
    CONSERVATIVE("Conservative"),
    MODERATE("Moderate"),
    AGGRESSIVE("Aggressive"),
    UNLIMITED("Unlimited"),
}

fun AppSettings.hiddenTagTokens(): Set<String> = parseFilterTokens(hiddenTags)

fun AppSettings.mutedTagTokens(): Set<String> = parseFilterTokens(mutedTags)

private fun parseFilterTokens(raw: String): Set<String> =
    raw
        .split(',', '\n')
        .map { token -> token.trim().removePrefix("#").lowercase() }
        .filter { token -> token.isNotBlank() }
        .toSet()
