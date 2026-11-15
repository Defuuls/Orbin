package com.orbin.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.orbin.core.model.watchedNewReplies
import com.orbin.uinext.LibrarySection
import com.orbin.uinext.NextDestination
import com.orbin.uinext.OrbinBottomBar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Queue membership changes the badge; per-byte transfer updates do not recompose the reader. */
@Composable
internal fun rememberActiveDownloadBadge(downloads: MediaDownloads?): Int {
    val count by remember(downloads) {
        downloads
            ?.records
            ?.map { records -> records.count { it.status.isActive } }
            ?.distinctUntilChanged()
            ?: flowOf(0)
    }.collectAsState(0)
    return count
}

/** New replies across watched threads, for the Library tab's badge. */
@Composable
internal fun rememberWatchedBadge(browser: Browser): Int {
    val count by remember(browser) {
        browser.savedThreads.map { it.watchedNewReplies() }.distinctUntilChanged()
    }.collectAsState(0)
    return count
}

@Composable
internal fun PrimaryNavigation(
    browser: Browser,
    downloads: MediaDownloads?,
    selected: NextDestination,
) {
    OrbinBottomBar(
        selected = selected,
        onSelect = browser::openPrimaryDestination,
        downloadBadge = rememberActiveDownloadBadge(downloads),
        watchedBadge = rememberWatchedBadge(browser),
    )
}

/** A home-screen quick action: Downloads and Watched open their Library sections above Feed. */
internal fun Browser.openQuickAction(tab: Route) {
    when (tab) {
        Route.Downloads -> openLibrary(LibrarySection.DOWNLOADS)
        Route.Watched -> openLibrary(LibrarySection.WATCHED)
        else -> openTab(tab)
    }
}

/** The route that shows a Library [LibrarySection]. */
internal fun LibrarySection.route(): Route =
    when (this) {
        LibrarySection.WATCHED -> Route.Watched
        LibrarySection.SAVED -> Route.Saved
        LibrarySection.DOWNLOADS -> Route.Downloads
    }

/** Primary tabs replace one another; Library reopens its last section above Feed. */
internal fun Browser.openPrimaryDestination(destination: NextDestination) {
    when (destination) {
        NextDestination.FEED -> openTab(Route.Feed)
        NextDestination.BOARDS -> openTab(Route.Boards)
        NextDestination.LIBRARY -> openLibrary()
        NextDestination.WATCHED -> openLibrary(LibrarySection.WATCHED)
        NextDestination.SAVED -> openLibrary(LibrarySection.SAVED)
        NextDestination.DOWNLOADS -> openLibrary(LibrarySection.DOWNLOADS)
        NextDestination.SETTINGS -> openPrimaryPage(Route.Settings)
        NextDestination.SEARCH -> open(Route.Search)
    }
}
