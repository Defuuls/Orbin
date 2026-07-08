package com.orbin.core.model

/**
 * Preload mode configuration. Controls which types of media are preloaded from the CDN.
 * Used in conjunction with throttling to avoid rate limiting.
 */
enum class PreloadOption(
    val label: String,
) {
    NONE("Disabled"),
    THUMBNAILS("Thumbnails only"),
    IMAGES("Thumbnails + Images"),
    VIDEOS("Thumbnails + Images + Videos"),
    ALL("All media"),
    ;

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
    val delayBetweenRequests: Long = 250,
    val maxPerMinute: Int = 0,
) {
    init {
        require(maxConcurrent in MIN_CONCURRENT..MAX_CONCURRENT) {
            "maxConcurrent must be between $MIN_CONCURRENT and $MAX_CONCURRENT"
        }
        require(delayBetweenRequests >= 0) { "delayBetweenRequests must be non-negative" }
        require(maxPerMinute >= 0) { "maxPerMinute must be non-negative" }
    }

    companion object {
        private const val MIN_CONCURRENT = 1
        private const val MAX_CONCURRENT = 5
        private const val CONSERVATIVE_CONCURRENT = 1
        private const val CONSERVATIVE_DELAY = 500L
        private const val CONSERVATIVE_PER_MINUTE = 30
        private const val MODERATE_CONCURRENT = 2
        private const val MODERATE_DELAY = 250L
        private const val MODERATE_PER_MINUTE = 60
        private const val AGGRESSIVE_CONCURRENT = 3
        private const val AGGRESSIVE_DELAY = 100L
        private const val AGGRESSIVE_PER_MINUTE = 120

        val Conservative =
            PreloadThrottling(
                maxConcurrent = CONSERVATIVE_CONCURRENT,
                delayBetweenRequests = CONSERVATIVE_DELAY,
                maxPerMinute = CONSERVATIVE_PER_MINUTE,
            )
        val Moderate =
            PreloadThrottling(
                maxConcurrent = MODERATE_CONCURRENT,
                delayBetweenRequests = MODERATE_DELAY,
                maxPerMinute = MODERATE_PER_MINUTE,
            )
        val Aggressive =
            PreloadThrottling(
                maxConcurrent = AGGRESSIVE_CONCURRENT,
                delayBetweenRequests = AGGRESSIVE_DELAY,
                maxPerMinute = AGGRESSIVE_PER_MINUTE,
            )
        val Default = Moderate
    }
}
