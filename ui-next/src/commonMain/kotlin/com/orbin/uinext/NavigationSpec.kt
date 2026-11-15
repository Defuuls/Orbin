package com.orbin.uinext

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_nav_boards
import com.orbin.uinext.resources.reader_nav_downloads
import com.orbin.uinext.resources.reader_nav_feed
import com.orbin.uinext.resources.reader_nav_home
import com.orbin.uinext.resources.reader_nav_saved
import com.orbin.uinext.resources.reader_nav_search
import com.orbin.uinext.resources.reader_nav_settings
import com.orbin.uinext.resources.reader_nav_watched
import org.jetbrains.compose.resources.StringResource

/** Where a destination can appear. Every navigation surface renders from [ReaderNavigationSpec]. */
enum class NavSurface { BAR, RAIL, DRAWER }

/** Window size classes the navigation distinguishes; see [ReaderNavigationSpec.EXPANDED_MIN_WIDTH]. */
enum class NavWindow {
    COMPACT,
    EXPANDED,
    ;

    companion object {
        fun of(width: Dp): NavWindow = if (width >= ReaderNavigationSpec.EXPANDED_MIN_WIDTH) EXPANDED else COMPACT
    }
}

/** One destination: what it is called, how it is drawn, and which surface shows it per window size. */
@Immutable
class NavEntry internal constructor(
    val destination: NextDestination,
    val label: StringResource,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    private val surfaces: Map<NavWindow, Set<NavSurface>>,
    /** The drawer still says "Home" and keeps its own order until it is retired. */
    internal val drawerLabel: StringResource = label,
    internal val drawerRank: Int = Int.MAX_VALUE,
) {
    fun shownOn(
        surface: NavSurface,
        window: NavWindow,
    ): Boolean = surface in surfaces[window].orEmpty()

    fun icon(selected: Boolean): ImageVector = if (selected) selectedIcon else icon
}

/**
 * The single, ordered definition of Orbin's navigation, shared by Android and iOS.
 *
 * The bottom bar, the wide-window rail and the drawer all render from [entries]; each app only maps
 * a [NextDestination] to its own route.
 */
object ReaderNavigationSpec {
    /** From this width the primary destinations move from the bottom bar into a side rail. */
    val EXPANDED_MIN_WIDTH: Dp = 840.dp

    private val primary =
        mapOf(NavWindow.COMPACT to setOf(NavSurface.BAR), NavWindow.EXPANDED to setOf(NavSurface.RAIL))
    private val primaryAndDrawer =
        mapOf(
            NavWindow.COMPACT to setOf(NavSurface.BAR, NavSurface.DRAWER),
            NavWindow.EXPANDED to setOf(NavSurface.RAIL, NavSurface.DRAWER),
        )
    private val drawerOnly = NavWindow.entries.associateWith { setOf(NavSurface.DRAWER) }

    val entries: List<NavEntry> by lazy {
        listOf(
            NavEntry(
                NextDestination.BOARDS,
                Res.string.reader_nav_boards,
                ReaderIcons.Boards,
                ReaderIcons.BoardsFilled,
                primaryAndDrawer,
                drawerRank = 2,
            ),
            NavEntry(
                NextDestination.FEED,
                Res.string.reader_nav_feed,
                ReaderIcons.Home,
                ReaderIcons.HomeFilled,
                primaryAndDrawer,
                drawerLabel = Res.string.reader_nav_home,
                drawerRank = 0,
            ),
            NavEntry(
                NextDestination.WATCHED,
                Res.string.reader_nav_watched,
                ReaderIcons.Watched,
                ReaderIcons.WatchedFilled,
                drawerOnly,
                drawerRank = 1,
            ),
            NavEntry(
                NextDestination.SEARCH,
                Res.string.reader_nav_search,
                ReaderIcons.Search,
                ReaderIcons.Search,
                drawerOnly,
                drawerRank = 3,
            ),
            NavEntry(
                NextDestination.SAVED,
                Res.string.reader_nav_saved,
                ReaderIcons.Saved,
                ReaderIcons.SavedFilled,
                drawerOnly,
                drawerRank = 4,
            ),
            NavEntry(
                NextDestination.DOWNLOADS,
                Res.string.reader_nav_downloads,
                ReaderIcons.Download,
                ReaderIcons.Download,
                primary,
            ),
            NavEntry(
                NextDestination.SETTINGS,
                Res.string.reader_nav_settings,
                ReaderIcons.Settings,
                ReaderIcons.Settings,
                primaryAndDrawer,
                drawerRank = 5,
            ),
        )
    }

    fun entry(destination: NextDestination): NavEntry = entries.first { it.destination == destination }

    fun entries(
        surface: NavSurface,
        window: NavWindow,
    ): List<NavEntry> {
        val shown = entries.filter { it.shownOn(surface, window) }
        return if (surface == NavSurface.DRAWER) shown.sortedBy { it.drawerRank } else shown
    }

    fun destinations(
        surface: NavSurface,
        window: NavWindow,
    ): List<NextDestination> = entries(surface, window).map { it.destination }
}
