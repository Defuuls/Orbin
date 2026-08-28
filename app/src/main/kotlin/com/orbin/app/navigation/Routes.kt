package com.orbin.app.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes (Navigation Compose 2.8). Each destination is a `@Serializable`
 * type; arguments are real fields, so navigation is checked at compile time and ViewModels read
 * their arguments via `SavedStateHandle.toRoute<…>()`.
 */
sealed interface Route {
    @Serializable
    data object Home : Route

    @Serializable
    data object SubscribedFeed : Route

    /**
     * The redesigned feed. It reads the same subscribed-feed state as [SubscribedFeed]; the two are
     * separate destinations so the previous feed stays reachable and comparable while the rest of
     * the interface catches up.
     */
    @Serializable
    data object NextFeed : Route

    @Serializable
    data object BoardGallery : Route

    @Serializable
    data object Search : Route

    @Serializable
    data object History : Route

    @Serializable
    data object GalleryBrowser : Route

    @Serializable
    data object AllMedia : Route

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

    /**
     * The previous thread reader, opened from inside the new one. Separate destination rather than
     * a mode so the reader that has not been ported yet keeps working exactly as it did, with the
     * per-post collapsing, layout toggle and thumbnail-size controls the new one does not carry.
     */
    @Serializable
    data class ClassicThread(
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
         * thread. The all-media wall is such a caller: it holds a catalog's files, not the thread's
         * full ordering, so it cannot supply a meaningful [startIndex].
         */
        val attachmentId: String? = null,
    ) : Route

    @Serializable
    data object Downloads : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object SettingsContent : Route

    @Serializable
    data object SettingsNotifications : Route

    @Serializable
    data object SettingsAppearance : Route

    @Serializable
    data object SettingsMedia : Route

    @Serializable
    data object SettingsPrivacy : Route

    @Serializable
    data object SettingsAdvanced : Route

    @Serializable
    data object SettingsStorage : Route

    @Serializable
    data object SettingsSearch : Route

    @Serializable
    data object Subscriptions : Route

    @Serializable
    data object Onboarding : Route
}
