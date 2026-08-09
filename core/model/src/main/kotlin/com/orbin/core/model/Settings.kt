package com.orbin.core.model

import kotlinx.serialization.Serializable

/** Theme preference independent of any UI framework type (mapped to the design-system enum in app). */
@Serializable
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Serializable
enum class ColorTheme(
    val label: String,
) {
    ORBIN("Default"),
    TOMORROW("Tomorrow"),
    TOMORROW_NIGHT("Tomorrow Dark"),
}

/** App icon variant for home screen. */
@Serializable
enum class AppIconVariant(
    val label: String,
) {
    DEFAULT("Orbital Orb"),
    NESTED_RINGS("Nested Rings"),
    ABSTRACT_FLOW("Abstract Flow"),
    MINIMALIST_ESSENCE("Minimalist Essence"),
    DUAL_GRADIENT("Dual Gradient"),
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val FIVE_MINUTES_MS = 5 * MILLIS_PER_MINUTE
private const val FIFTEEN_MINUTES_MS = 15 * MILLIS_PER_MINUTE
private const val THIRTY_MINUTES_MS = 30 * MILLIS_PER_MINUTE

private const val FEED_LIMIT_SIX = 6
private const val FEED_LIMIT_TWELVE = 12
private const val FEED_LIMIT_EIGHTEEN = 18
private const val THUMBNAIL_SIZE_COMPACT_DP = 80
private const val THUMBNAIL_SIZE_MEDIUM_DP = 96
private const val THUMBNAIL_SIZE_LARGE_DP = 120
private const val THUMBNAIL_SIZE_FILL_DP = 240

@Serializable
enum class FeedThreadLimit(
    val count: Int?,
    val label: String,
) {
    SIX(FEED_LIMIT_SIX, "6"),
    TWELVE(FEED_LIMIT_TWELVE, "12"),
    EIGHTEEN(FEED_LIMIT_EIGHTEEN, "18"),
    ALL(null, "All"),
}

@Serializable
enum class DohProvider(
    val label: String,
) {
    CLOUDFLARE("Cloudflare"),
    OPENDNS("OpenDNS"),
    NEXTDNS("NextDNS"),
}

/**
 * How stale the subscribed feed may be before returning to it triggers a reload.
 *
 * [staleAfterMillis] is the age past which the cached feed is discarded: zero always reloads, and
 * null never does. The two ends are what the old on/off setting used to express.
 */
@Serializable
enum class FeedRefreshInterval(
    val label: String,
    val staleAfterMillis: Long?,
) {
    ALWAYS("Always", 0),
    ONE_MINUTE("1 min", MILLIS_PER_MINUTE),
    FIVE_MINUTES("5 min", FIVE_MINUTES_MS),
    FIFTEEN_MINUTES("15 min", FIFTEEN_MINUTES_MS),
    THIRTY_MINUTES("30 min", THIRTY_MINUTES_MS),
    NEVER("Never", null),
}

/** How a tapped thread is presented. */
@Serializable
enum class ThreadPresentation(
    val label: String,
) {
    /** Pushes as a page: the feed slides away with it, the usual Android forward navigation. */
    PAGE("Page"),

    /** Slides in over the feed, which stays in place underneath and is revealed on the way back. */
    OVERLAY("Slide over"),
}

/**
 * How downloaded media files are organized on disk, layered under the Orbin downloads folder
 * (or the user's chosen SAF folder). Filenames themselves are unaffected — this only controls
 * what subfolders, if any, a download lands in.
 */
@Serializable
enum class DownloadOrganization(
    val label: String,
) {
    FLAT("Flat (single folder)"),
    BY_BOARD("By board"),
    BY_BOARD_THEN_THREAD("By board, then thread"),
    BY_THREAD("By thread"),
}

@Serializable
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
@Serializable
data class AppSettings(
    val personalizedHomeFeed: Boolean = true,
    val hiddenTags: String = "",
    val mutedTags: String = "",
    val hideNsfwBoards: Boolean = false,
    val hideTextOnlyThreads: Boolean = false,
    /** How stale the subscribed feed may be before returning to it reloads it. */
    val feedRefreshInterval: FeedRefreshInterval = FeedRefreshInterval.ALWAYS,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val colorTheme: ColorTheme = ColorTheme.ORBIN,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val fontScale: Float = 1f,
    val appIconVariant: AppIconVariant = AppIconVariant.DEFAULT,
    val fullScreenFeedChrome: Boolean = false,
    val threadPresentation: ThreadPresentation = ThreadPresentation.PAGE,
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
    val downloadOrganization: DownloadOrganization = DownloadOrganization.FLAT,
    val userAgent: String = "",
    val dohProvider: DohProvider = DohProvider.CLOUDFLARE,
    val httpsOnly: Boolean = true,
    val connectTimeoutSeconds: Long = 15,
    val readTimeoutSeconds: Long = 30,
    val disableOcspChecking: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val saveRecentSearches: Boolean = false,
    val internalUpdaterEnabled: Boolean = true,
    val threadWatchNotificationsEnabled: Boolean = true,
    /** Quiet hours start time in HH:MM format (24-hour), empty string = disabled. */
    val quietHoursStart: String = "",
    /** Quiet hours end time in HH:MM format (24-hour), empty string = disabled. */
    val quietHoursEnd: String = "",
    val activeProviderId: String = "",
    val onboardingCompleted: Boolean = false,
    val mediaScrollThreadView: Boolean = true,
    val mediaScrollBoardView: Boolean = false,
    /**
     * Autoplay each thread's first attachment inline in the subscribed feed when it's a video,
     * muted per [muteByDefault], while its row is on screen. Other attachments stay static
     * thumbnails until the thread is opened.
     */
    val autoplayVideosInFeed: Boolean = false,
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
@Serializable
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
