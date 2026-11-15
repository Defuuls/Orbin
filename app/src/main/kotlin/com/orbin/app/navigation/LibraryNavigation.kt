package com.orbin.app.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import com.orbin.uinext.LibrarySection
import com.orbin.uinext.NextDestination

/** The route each Library section opens. */
internal fun LibrarySection.route(): Route =
    when (this) {
        LibrarySection.WATCHED -> Route.Watched
        LibrarySection.SAVED -> Route.Saved
        LibrarySection.DOWNLOADS -> Route.Downloads
    }

internal fun NavDestination.librarySection(): LibrarySection? =
    LibrarySection.entries.firstOrNull { section ->
        when (section) {
            LibrarySection.WATCHED -> hasRoute(Route.Watched::class)
            LibrarySection.SAVED -> hasRoute(Route.Saved::class)
            LibrarySection.DOWNLOADS -> hasRoute(Route.Downloads::class)
        }
    }

/** Which primary page is on screen, for the bar and rail selection; detail pages have none. */
internal fun NavDestination.primaryNavigationDestination(): NextDestination? =
    when {
        hasRoute(Route.NextFeed::class) -> NextDestination.FEED
        hasRoute(Route.BoardGallery::class) -> NextDestination.BOARDS
        hasRoute(Route.Settings::class) -> NextDestination.SETTINGS
        else -> librarySection()?.destination
    }

/** The Library section last shown, so the Library tab reopens where the reader left it. */
@Stable
internal class LibraryMemory(
    initial: LibrarySection = LibrarySection.DEFAULT,
) {
    var section by mutableStateOf(initial)

    companion object {
        val Saver: Saver<LibraryMemory, String> =
            Saver(save = { it.section.name }, restore = { LibraryMemory(LibrarySection.valueOf(it)) })
    }
}

internal val LocalLibraryMemory = staticCompositionLocalOf { LibraryMemory() }

/** Opens [section] as the Library tab: Back from any section returns to Feed, as on iOS. */
internal fun NavHostController.openLibrary(
    section: LibrarySection,
    memory: LibraryMemory? = null,
) {
    memory?.section = section
    navigateToTab(section.route())
}

/**
 * Entry points from outside the reader. Settings' "watched" link and the Watched shortcut open
 * Library on Watched; a watched-thread notification opens its thread above Library/Watched, so Back
 * from the thread lands in Watched.
 */
internal fun NavHostController.openSettingsLink(
    link: String,
    memory: LibraryMemory? = null,
    openUri: (String) -> Unit = {},
) {
    when (link) {
        "boards", "favorites" -> navigateToTab(Route.BoardGallery)
        "history" -> navigate(Route.History)
        "watched" -> openLibrary(LibrarySection.WATCHED, memory)
        "downloads" -> openLibrary(LibrarySection.DOWNLOADS, memory)
        "help" -> openUri("https://github.com/Defuuls/Orbin/wiki")
    }
}

internal fun NavHostController.openPendingRoute(
    route: Route,
    memory: LibraryMemory? = null,
) {
    when (route) {
        is Route.Thread -> {
            openLibrary(LibrarySection.WATCHED, memory)
            navigate(route)
        }
        Route.Watched -> openLibrary(LibrarySection.WATCHED, memory)
        Route.Saved -> openLibrary(LibrarySection.SAVED, memory)
        Route.Downloads -> openLibrary(LibrarySection.DOWNLOADS, memory)
        Route.NextFeed, Route.BoardGallery, Route.Search -> navigateToTab(route)
        else -> navigate(route)
    }
}
