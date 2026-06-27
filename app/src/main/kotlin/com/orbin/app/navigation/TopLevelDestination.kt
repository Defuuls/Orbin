package com.orbin.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

/** The four top-level destinations shown in the bottom navigation bar. */
enum class TopLevelDestination(
    val route: Route,
    val label: String,
    val icon: ImageVector,
) {
    BOARDS(Route.Home, "Boards", Icons.AutoMirrored.Filled.List),
    SEARCH(Route.Search, "Search", Icons.Filled.Search),
    BOOKMARKS(Route.Bookmarks, "Bookmarks", Icons.Filled.Bookmark),
    HISTORY(Route.History, "History", Icons.Filled.History),
}
