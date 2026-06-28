package com.orbin.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

/** The four top-level destinations shown in the bottom navigation bar. */
enum class TopLevelDestination(
    val route: Route,
    val label: String,
    val icon: ImageVector,
) {
    FEED(Route.SubscribedFeed, "Feed", Icons.Filled.DynamicFeed),
    SEARCH(Route.Search, "Search", Icons.Filled.Search),
    BOOKMARKS(Route.Bookmarks, "Bookmarks", Icons.Filled.Bookmark),
    HISTORY(Route.History, "History", Icons.Filled.History),
}
