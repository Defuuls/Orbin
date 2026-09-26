package com.orbin.core.model

import kotlinx.serialization.Serializable

/** Theme preference independent of any UI framework type (mapped to the design-system enum in app). */
@Serializable
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** App icon variant for home screen. */
@Serializable
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
    val hideNsfwBoards: Boolean = false,
    /** Spoiler-covers the media of posts labelled as violent; see [ViolentMediaCover]. On by default. */
    val coverViolentMedia: Boolean = true,
    /**
     * Whether the all-media wall follows its catalog sweep with a slow pass through every thread
     * it found, pulling in the media attached to replies.
     *
     * Off by default, and deliberately so: the sweep itself is one request per board, while this
     * is one request per *thread* — thousands per sweep. It is trickled out at roughly one request
     * a second to stay inside what providers ask for, which means it fills over hours rather than
     * minutes, and it only runs while the wall is open.
     */
    val deepMediaScan: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val activeProviderId: String = "",
    val onboardingCompleted: Boolean = false,
    /** Feed columns on an unfolded foldable's inner screen, 1–3. The front screen is always one. */
    val unfoldedFeedColumns: Int = DEFAULT_WIDE_FEED_COLUMNS,
    /** Feed columns on a tablet, 1–4. */
    val tabletFeedColumns: Int = DEFAULT_WIDE_FEED_COLUMNS,
) {
    companion object {
        val Default = AppSettings()
    }
}

/** Two columns until the reader picks otherwise: a wide screen's first step up from a phone. */
const val DEFAULT_WIDE_FEED_COLUMNS = 2

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
