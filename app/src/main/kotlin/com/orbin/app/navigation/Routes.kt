package com.orbin.app.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes (Navigation Compose 2.8). Each destination is a `@Serializable`
 * type; arguments are real fields, so navigation is checked at compile time and ViewModels read
 * their arguments via `SavedStateHandle.toRoute<…>()`.
 */
sealed interface Route {
    /** The subscribed feed: every board you follow, merged and ordered by activity. */
    @Serializable
    data object NextFeed : Route

    @Serializable
    data object BoardGallery : Route

    @Serializable
    data object Search : Route

    /** One board's media as a wall, opened from that board's catalog. */
    @Serializable
    data class BoardMedia(
        val board: String,
    ) : Route

    @Serializable
    data class Board(
        val provider: String,
        val board: String,
        val title: String,
    ) : Route

    @Serializable
    data class Thread(
        val provider: String,
        val board: String,
        val thread: Long,
        val title: String,
    ) : Route

    @Serializable
    data class Gallery(
        val provider: String,
        val board: String,
        val thread: Long,
        val startIndex: Int,
        /**
         * Which file to open at, when the caller knows the attachment but not its index in the
         * thread. A board's media wall is such a caller: it holds a catalog's files, not the
         * thread's full ordering, so it cannot supply a meaningful [startIndex].
         */
        val attachmentId: String? = null,
    ) : Route

    /** What you downloaded: the middle tab. */
    @Serializable
    data object Downloads : Route

    /** One settings destination, not eight. */
    @Serializable
    data object Settings : Route

    @Serializable
    data object Onboarding : Route
}
