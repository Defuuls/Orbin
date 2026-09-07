package com.orbin.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

/** The top-level destinations shown in the bottom navigation bar. */
enum class TopLevelDestination(
    val route: Route,
    val label: String,
    val icon: ImageVector,
) {
    FEED(Route.NextFeed, "Feed", Icons.Filled.DynamicFeed),

    /** All Media is the primary gallery; board→thread GalleryBrowser is secondary via Command. */
    MEDIA(Route.AllMedia, "Media", Icons.Filled.PhotoLibrary),
}
