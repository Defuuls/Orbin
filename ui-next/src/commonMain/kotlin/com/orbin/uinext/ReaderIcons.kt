package com.orbin.uinext

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Clover's standard Material toolbar symbols, with Orbin-specific optional reader symbols.
 * Every icon retains the shared 24dp canvas and stable layout and accessibility targets.
 */
object ReaderIcons {
    /**
     * Coral ring mark from the Orbin Coral design: a thick open orbit, tinted by the caller.
     * The path is a near-complete circle with a gap at the lower right and round caps.
     */
    val Logo: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "ReaderOrbinLogo",
                defaultWidth = READER_ICON_SIZE.dp,
                defaultHeight = READER_ICON_SIZE.dp,
                viewportWidth = READER_ICON_SIZE,
                viewportHeight = READER_ICON_SIZE,
            ).addPath(
                pathData = PathParser().parsePathString(LOGO_RING).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = LOGO_STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ).build()
    }
    val Plus: ImageVector by lazy { outline("Plus", "M12 5v14M5 12h14") }
    val Heart: ImageVector by lazy { outline("Heart", HEART) }
    val HeartFilled: ImageVector by lazy { filled("HeartFilled", HEART) }
    val Home: ImageVector by lazy { outline("Home", HOME) }
    val HomeFilled: ImageVector by lazy { filled("HomeFilled", HOME) }
    val Search: ImageVector get() = Icons.Filled.Search
    val Boards: ImageVector by lazy { outline("Boards", BOARDS) }
    val BoardsFilled: ImageVector by lazy { filled("BoardsFilled", BOARDS) }
    val Album: ImageVector get() = Icons.Filled.PhotoLibrary
    val Watched: ImageVector by lazy {
        outline("Watched", "$BELL M12 2v1M10 20.5a2 2 0 0 0 4 0")
    }
    val WatchedFilled: ImageVector by lazy {
        filled("WatchedFilled", "$BELL M11 2a1 1 0 0 1 2 0v1h-2ZM10 20h4a2 2 0 0 1-4 0Z")
    }
    val Saved: ImageVector get() = Icons.Filled.BookmarkBorder
    val SavedFilled: ImageVector get() = Icons.Filled.Bookmark
    val Chat: ImageVector by lazy {
        outline(
            "Chat",
            "M21 11.5c0 2-.6 3.7-1.7 5.2L21 21l-4.5-1.4A9.7 9.7 0 0 1 12 20" +
                "c-5 0-9-3.8-9-8.5S7 3 12 3s9 3.8 9 8.5Z",
        )
    }
    val ChatFilled: ImageVector by lazy {
        filled(
            "ChatFilled",
            "M21.5 11.4c0 5-4.2 9-9.5 9-1.1 0-2.2-.2-3.2-.5L3 22l1.6-5.3A8.6 8.6 0 0 1 2.5 11.4" +
                "c0-5 4.2-9 9.5-9s9.5 4 9.5 9Z",
        )
    }
    val ShareForward: ImageVector by lazy {
        filled(
            "ShareForward",
            "M13.5 3 23 11.5 13.5 20v-5c-5.1 0-8.5 1.4-12 5 1.6-7.1 5.6-10.5 12-10.5Z",
        )
    }
    val Music: ImageVector by lazy {
        outline(
            "Music",
            "M9 18V5l11-2v13M9 8l11-2M9 18c0 1.7-1.7 3-3.5 3S2 20.2 2 18.5 3.7 16 5.5 16 9 16.3 9 18ZM20 16c0 1.7-1.7 3-3.5 3s-3.5-.8-3.5-2.5S14.7 14 16.5 14s3.5.3 3.5 2Z",
        )
    }
    val Share: ImageVector get() = Icons.Filled.Share
    val Menu: ImageVector get() = Icons.Filled.Menu
    val More: ImageVector get() = Icons.Filled.MoreVert
    val MoreVertical: ImageVector get() = Icons.Filled.MoreVert
    val Refresh: ImageVector get() = Icons.Filled.Refresh
    val Back: ImageVector get() = Icons.AutoMirrored.Filled.ArrowBack
    val ChevronDown: ImageVector by lazy { outline("ChevronDown", "M6 9l6 6 6-6") }
    val ChevronRight: ImageVector by lazy { outline("ChevronRight", "M9 5l7 7-7 7", autoMirror = true) }
    val Close: ImageVector get() = Icons.Filled.Close
    val Pin: ImageVector by lazy { outline("Pin", "M8 3h8M9 3v6l-3 4v2h12v-2l-3-4V3M12 15v7") }
    val Lock: ImageVector by lazy {
        outline("Lock", "M6 10h12v11H6ZM8 10V6a4 4 0 0 1 8 0v4M12 14v3")
    }
    val Check: ImageVector by lazy { outline("Check", "M5 12l4 4L19 6") }
    val Download: ImageVector get() = Icons.Filled.FileDownload
    val Star: ImageVector by lazy { outline("Star", STAR) }
    val StarFilled: ImageVector by lazy { filled("StarFilled", STAR) }
    val Settings: ImageVector get() = Icons.Filled.Settings
    val Play: ImageVector by lazy {
        outline("Play", "$CIRCLE M10 8l6 4-6 4Z")
    }
    val Clock: ImageVector by lazy { outline("Clock", "$CIRCLE M12 7v5l3.5 2.5") }
    val Shield: ImageVector by lazy {
        outline(
            "Shield",
            "M12 3c-2.5 1.8-5.3 2.7-8 3v5.4c0 4.6 3.5 8.1 8 10.1 4.5-2 8-5.5 8-10.1V6c-2.7-.3-5.5-1.2-8-3Z",
        )
    }
    val Hide: ImageVector by lazy {
        outline(
            "Hide",
            "M3 12s3-6 9-6 9 6 9 6-3 6-9 6-9-6-9-6Z" +
                "M15 12a3 3 0 1 1-6 0a3 3 0 0 1 6 0ZM3 3l18 18",
        )
    }
    val Storage: ImageVector by lazy {
        outline("Storage", "M3 3.5h18v4H3ZM5 7.5V20h14V7.5M10 11.5h4")
    }
    val Info: ImageVector by lazy { outline("Info", "$CIRCLE M12 10.5V17M12 7h.01") }
    val Help: ImageVector by lazy {
        outline("Help", "$CIRCLE M9.5 8.5a2.5 2.5 0 0 1 5 .2c0 1.8-2.5 2.1-2.5 4.3M12 17h.01")
    }
    val Palette: ImageVector by lazy {
        outline(
            "Palette",
            "M12 3c-5 0-9 3.8-9 8.5S6.6 20 11 20h1.2c1.2 0 1.6-1.3.8-2.1l-.6-.6c-.8-.8-.3-2.3.9-2.3H16" +
                "c3 0 5-2 5-4.7C21 6.2 16.9 3 12 3ZM7 11h.01M9 7.5h.01M13 6.5h.01M17 9h.01",
        )
    }
    val Globe: ImageVector by lazy {
        outline("Globe", "$CIRCLE M3 12h18M12 3c3 3 4 6 4 9s-1 6-4 9c-3-3-4-6-4-9s1-6 4-9Z")
    }

    private const val HOME =
        "M3 10.2l8.1-6.8c.5-.5 1.3-.5 1.8 0l8.1 6.8V20c0 .6-.4 1-1 1h-5v-7c0-.6-.4-1-1-1h-4" +
            "c-.6 0-1 .4-1 1v7H4c-.6 0-1-.4-1-1Z"
    private const val BOARDS =
        "M4 3h5c.6 0 1 .4 1 1v5c0 .6-.4 1-1 1H4c-.6 0-1-.4-1-1V4c0-.6.4-1 1-1Z" +
            "M15 3h5c.6 0 1 .4 1 1v5c0 .6-.4 1-1 1h-5c-.6 0-1-.4-1-1V4c0-.6.4-1 1-1Z" +
            "M4 14h5c.6 0 1 .4 1 1v5c0 .6-.4 1-1 1H4c-.6 0-1-.4-1-1v-5c0-.6.4-1 1-1Z" +
            "M15 14h5c.6 0 1 .4 1 1v5c0 .6-.4 1-1 1h-5c-.6 0-1-.4-1-1v-5c0-.6.4-1 1-1Z"
    private const val BELL = "M6 9a6 6 0 0 1 12 0c0 5.5 2 6.5 2 8H4c0-1.5 2-2.5 2-8Z"
    private const val STAR = "M12 3l2.8 5.7 6.3.9-4.5 4.4 1.1 6.3-5.7-3-5.7 3 1.1-6.3-4.5-4.4 6.3-.9Z"
    private const val HEART =
        "M12 21 3.3 12.7A5.6 5.6 0 0 1 12 5.6a5.6 5.6 0 0 1 8.7 7.1Z"
    private const val CIRCLE = "M21 12a9 9 0 1 1-18 0a9 9 0 0 1 18 0Z"

    // Open ring, gap roughly at 4 o'clock so the mark reads as an orbit rather than a plain O.
    private const val LOGO_RING = "M17.85 16.55A6.9 6.9 0 1 1 18.05 8.15"
}

private fun outline(
    name: String,
    data: String,
    autoMirror: Boolean = false,
): ImageVector = readerIcon(name, data, filled = false, autoMirror = autoMirror)

private fun filled(
    name: String,
    data: String,
): ImageVector = readerIcon(name, data, filled = true)

private fun readerIcon(
    name: String,
    data: String,
    filled: Boolean,
    autoMirror: Boolean = false,
): ImageVector =
    ImageVector
        .Builder(
            name = "Reader$name",
            defaultWidth = READER_ICON_SIZE.dp,
            defaultHeight = READER_ICON_SIZE.dp,
            viewportWidth = READER_ICON_SIZE,
            viewportHeight = READER_ICON_SIZE,
            autoMirror = autoMirror,
        ).addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            pathFillType = PathFillType.EvenOdd,
            fill = if (filled) SolidColor(Color.Black) else null,
            stroke = if (filled) null else SolidColor(Color.Black),
            strokeLineWidth = if (filled) 0f else READER_ICON_STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

private const val READER_ICON_SIZE = 24f
private const val READER_ICON_STROKE = 1.8f
private const val LOGO_STROKE = 3.15f
