package com.orbin.core.model

/**
 * Preload mode configuration. Controls which types of media are preloaded from the CDN.
 * Used in conjunction with throttling to avoid rate limiting.
 */
enum class PreloadOption(val label: String) {
    NONE("Disabled"),
    THUMBNAILS("Thumbnails only"),
    IMAGES("Thumbnails + Images"),
    VIDEOS("Thumbnails + Images + Videos"),
    ALL("All media");

    fun includesThumbnails(): Boolean = this != NONE

    fun includesImages(): Boolean = this in listOf(IMAGES, VIDEOS, ALL)

    fun includesVideos(): Boolean = this in listOf(VIDEOS, ALL)
}

/**
 * Throttling configuration for preload requests to prevent CDN rate limiting.
 * - maxConcurrent: maximum simultaneous preload requests (1-5 recommended)
 * - delayBetweenRequests: milliseconds between sequential requests
 * - maxPerMinute: maximum preload requests allowed per minute (0 = unlimited)
 */
data class PreloadThrottling(
    val maxConcurrent: Int = 1,
    val delayBetweenRequests: Long = 250, // ms
    val maxPerMinute: Int = 0, // 0 = unlimited
) {
    init {
        require(maxConcurrent in 1..5) { "maxConcurrent must be between 1 and 5" }
        require(delayBetweenRequests >= 0) { "delayBetweenRequests must be non-negative" }
        require(maxPerMinute >= 0) { "maxPerMinute must be non-negative" }
    }

    companion object {
        val Conservative =
            PreloadThrottling(maxConcurrent = 1, delayBetweenRequests = 500, maxPerMinute = 30)
        val Moderate =
            PreloadThrottling(maxConcurrent = 2, delayBetweenRequests = 250, maxPerMinute = 60)
        val Aggressive =
            PreloadThrottling(maxConcurrent = 3, delayBetweenRequests = 100, maxPerMinute = 120)
        val Default = Moderate
    }
}
