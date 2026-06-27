package com.orbin.provider.vichan

import com.orbin.core.model.ProviderId

/**
 * Configuration for a single vichan/4chan-compatible site. Because every URL and flag is data,
 * supporting another instance is a matter of constructing a different [VichanSite] — no code
 * changes. This is what keeps the example provider generic.
 */
data class VichanSite(
    val providerId: ProviderId,
    val displayName: String,
    /** Human-facing site URL (for display / "open in browser"). */
    val siteUrl: String,
    /** Base URL for the JSON API (Retrofit base; must end with '/'). */
    val apiBaseUrl: String,
    /** Base URL where full-size media is served. */
    val mediaBaseUrl: String,
    /** Base URL where thumbnails are served (often the same host as media). */
    val thumbBaseUrl: String,
    val mediaUrlStyle: MediaUrlStyle,
    val nsfwByDefault: Boolean = false,
    val supportsBoardList: Boolean = true,
) {
    companion object {
        /**
         * Default example instance: the well-known read-only 4chan JSON API. It is a stable,
         * public, read-only API which makes it ideal for demonstrating the provider end to end.
         * Replace or add [VichanSite]s to target any other instance.
         */
        val Example = VichanSite(
            providerId = ProviderId("fourchan"),
            displayName = "4chan (read-only example)",
            siteUrl = "https://boards.4chan.org",
            apiBaseUrl = "https://a.4cdn.org/",
            mediaBaseUrl = "https://i.4cdn.org",
            thumbBaseUrl = "https://i.4cdn.org",
            mediaUrlStyle = MediaUrlStyle.FOURCHAN,
            nsfwByDefault = false,
            supportsBoardList = true,
        )
    }
}

/** Two file-path conventions cover the vast majority of engines in this family. */
enum class MediaUrlStyle {
    /** `{base}/{board}/{tim}{ext}` for full, `{base}/{board}/{tim}s.jpg` for thumbnails. */
    FOURCHAN,

    /** `{base}/{board}/src/{tim}{ext}` for full, `{base}/{board}/thumb/{tim}{ext}` for thumbnails. */
    VICHAN,
}
