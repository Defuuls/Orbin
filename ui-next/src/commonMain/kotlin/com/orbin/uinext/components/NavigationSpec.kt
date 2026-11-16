package com.orbin.uinext.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_nav_boards
import com.orbin.uinext.resources.reader_nav_downloads
import com.orbin.uinext.resources.reader_nav_feed
import com.orbin.uinext.resources.reader_nav_library
import com.orbin.uinext.resources.reader_nav_saved
import com.orbin.uinext.resources.reader_nav_settings
import com.orbin.uinext.resources.reader_nav_watched
import org.jetbrains.compose.resources.StringResource

/** Where a primary destination appears. Both surfaces render from [ReaderNavigationSpec]. */
enum class NavSurface { BAR, RAIL }

/** Window size classes the navigation distinguishes; see [ReaderNavigationSpec.EXPANDED_MIN_WIDTH]. */
enum class NavWindow {
    COMPACT,
    EXPANDED,
    ;

    /** Phones get the bottom bar; wide windows get the rail, with the same destinations. */
    val surface: NavSurface get() = if (this == EXPANDED) NavSurface.RAIL else NavSurface.BAR

    companion object {
        fun of(width: Dp): NavWindow = if (width >= ReaderNavigationSpec.EXPANDED_MIN_WIDTH) EXPANDED else COMPACT
    }
}

/** One primary destination: what it is called, how it is drawn, and which surface shows it. */
@Immutable
class NavEntry internal constructor(
    val destination: NextDestination,
    val label: StringResource,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    fun shownOn(
        surface: NavSurface,
        window: NavWindow,
    ): Boolean = surface == window.surface

    fun icon(selected: Boolean): ImageVector = if (selected) selectedIcon else icon
}

/** The three pages grouped under the Library tab, in tab order. */
enum class LibrarySection(
    val destination: NextDestination,
    val label: StringResource,
    val icon: ImageVector,
) {
    WATCHED(NextDestination.WATCHED, Res.string.reader_nav_watched, ReaderIcons.Watched),
    SAVED(NextDestination.SAVED, Res.string.reader_nav_saved, ReaderIcons.Saved),
    DOWNLOADS(NextDestination.DOWNLOADS, Res.string.reader_nav_downloads, ReaderIcons.Download),
    ;

    companion object {
        /** Library opens on Watched until the reader has picked another section. */
        val DEFAULT = WATCHED

        fun of(destination: NextDestination?): LibrarySection? = entries.firstOrNull { it.destination == destination }
    }
}

/** The primary tab that owns [this]: a Library section highlights Library; Search highlights nothing. */
val NextDestination.primary: NextDestination?
    get() =
        when (this) {
            NextDestination.WATCHED, NextDestination.SAVED, NextDestination.DOWNLOADS -> NextDestination.LIBRARY
            NextDestination.SEARCH -> null
            else -> this
        }

/**
 * The single, ordered definition of Orbin's primary navigation, shared by Android and iOS:
 * Boards, Feed, Library, Settings, in a bottom bar on phones and a rail on wide windows.
 * Search lives in the top app bar. Each app only maps a [NextDestination] to its own route.
 */
object ReaderNavigationSpec {
    /** From this width the primary destinations move from the bottom bar into a side rail. */
    val EXPANDED_MIN_WIDTH: Dp = 840.dp

    val entries: List<NavEntry> by lazy {
        listOf(
            NavEntry(
                NextDestination.BOARDS,
                Res.string.reader_nav_boards,
                ReaderIcons.Boards,
                ReaderIcons.BoardsFilled,
            ),
            NavEntry(NextDestination.FEED, Res.string.reader_nav_feed, ReaderIcons.Home, ReaderIcons.HomeFilled),
            NavEntry(
                NextDestination.LIBRARY,
                Res.string.reader_nav_library,
                ReaderIcons.Saved,
                ReaderIcons.SavedFilled,
            ),
            NavEntry(
                NextDestination.SETTINGS,
                Res.string.reader_nav_settings,
                ReaderIcons.Settings,
                ReaderIcons.Settings,
            ),
        )
    }

    fun entry(destination: NextDestination): NavEntry =
        entries.first { it.destination == (destination.primary ?: destination) }

    fun entries(
        surface: NavSurface,
        window: NavWindow,
    ): List<NavEntry> = entries.filter { it.shownOn(surface, window) }

    fun destinations(
        surface: NavSurface,
        window: NavWindow,
    ): List<NextDestination> = entries(surface, window).map { it.destination }

    /** The page title of a primary destination; Library sections are titled Library. */
    fun titleOf(destination: NextDestination): StringResource? =
        entries.firstOrNull { it.destination == destination.primary }?.label
}
