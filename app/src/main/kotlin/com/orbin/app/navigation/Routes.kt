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
    data object Settings : Route
}
