package com.orbin.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    )
}

/** Primary pages replace one another; drawer destinations retain their existing root behavior. */
internal fun Browser.openPrimaryDestination(destination: NextDestination) {
    when (destination) {
        NextDestination.FEED -> openTab(Route.Feed)
        NextDestination.BOARDS -> openTab(Route.Boards)
        NextDestination.DOWNLOADS -> openPrimaryPage(Route.Downloads)
        NextDestination.SETTINGS -> openPrimaryPage(Route.Settings)
        NextDestination.SEARCH -> openTab(Route.Search)
        NextDestination.SAVED -> openTab(Route.Saved)
        NextDestination.WATCHED -> openTab(Route.Watched)
    }
}
