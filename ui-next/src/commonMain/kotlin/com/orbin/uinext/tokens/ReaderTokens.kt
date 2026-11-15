package com.orbin.uinext.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbin.uinext.components.NextAccentTheme

/*
 * The reader design's tokens, copied from docs/design-system/README.md.
 *
 * That document is the source of truth. scripts/tests/test_design_spec.py reads its tables and
 * the canonical token files and fails when a value differs, so change the document first and this file with it.
 * Keep one token per line in the shapes used below; the test reads them line by line.
 */

/** One theme's colors. The three instances below are the document's Light, Dark and AMOLED columns. */
@Immutable
data class ReaderColors(
    /** App bar ground. */
    val appBar: Color,
    /** Status bar ground, distinct from the app bar in Clover's default palette. */
    val statusBar: Color,
    /** App bar title and icons. */
    val onAppBar: Color,
    /** Page ground under grouped cards and content. */
    val background: Color,
    /** Solid grouped-card ground, thumbnail placeholder and skeleton bars. */
    val surfaceVariant: Color,
    /** Open thread's row in the two-pane catalog. */
    val selected: Color,
    /** Titles and post bodies. */
    val ink: Color,
    /** Generic secondary UI text, distinct from post details. */
    val inkSecondary: Color,
    /** Post numbers, dates, file details, helper text. */
    val inkMuted: Color,
    /** Row and post dividers. */
    val hairline: Color,
    /** Progress bar and text buttons. */
    val accent: Color,
    /** Text on an accent-filled control. */
    val onAccent: Color,
    /** Quoted (greentext) lines. */
    val quote: Color,
    /** Post author name. */
    val author: Color,
    /** Post and catalog subject text. */
    val subject: Color,
    /** Post reference links and backlinks. */
    val quoteLink: Color,
    /** Error state title, error text and the error icon; always shown with the icon, never color alone. */
    val error: Color,
    /** Text and icons on an error-filled control. */
    val onError: Color,
)

val ReaderLightColors =
    ReaderColors(
        appBar = Color(0xFFF4F5F7),
        statusBar = Color(0xFFF4F5F7),
        onAppBar = Color(0xFF14161F),
        background = Color(0xFFF4F5F7),
        surfaceVariant = Color(0xFFFFFFFF),
        selected = Color(0xFFFFF4ED),
        ink = Color(0xFF14161F),
        inkSecondary = Color(0xFF696F83),
        inkMuted = Color(0xFF696F83),
        hairline = Color(0x0F000000),
        accent = Color(0xFFC2410C),
        onAccent = Color(0xFFFFFFFF),
        quote = Color(0xFF2E7D32),
        author = Color(0xFF14161F),
        subject = Color(0xFF14161F),
        quoteLink = Color(0xFF0369A1),
        error = Color(0xFFBE185D),
        onError = Color(0xFFFFFFFF),
    )

val ReaderDarkColors =
    ReaderColors(
        appBar = Color(0xFF0B0D11),
        statusBar = Color(0xFF0B0D11),
        onAppBar = Color(0xFFE8EAF0),
        background = Color(0xFF0B0D11),
        surfaceVariant = Color(0xFF141520),
        selected = Color(0xFF2A1A10),
        ink = Color(0xFFE8EAF0),
        inkSecondary = Color(0xFF8A8FA8),
        inkMuted = Color(0xFF8A8FA8),
        hairline = Color(0x12FFFFFF),
        accent = Color(0xFFFB923C),
        onAccent = Color(0xFF431407),
        quote = Color(0xFF34D399),
        author = Color(0xFFFDBA74),
        subject = Color(0xFFE8EAF0),
        quoteLink = Color(0xFF7DD3FC),
        error = Color(0xFFF472B6),
        onError = Color(0xFF500724),
    )

val ReaderAmoledColors =
    ReaderColors(
        appBar = Color(0xFF000000),
        statusBar = Color(0xFF000000),
        onAppBar = Color(0xFFECECEF),
        background = Color(0xFF000000),
        surfaceVariant = Color(0xFF0E0E10),
        selected = Color(0xFF1F130B),
        ink = Color(0xFFECECEF),
        inkSecondary = Color(0xFF8C8C93),
        inkMuted = Color(0xFF8C8C93),
        hairline = Color(0x14FFFFFF),
        accent = Color(0xFFFB923C),
        onAccent = Color(0xFF431407),
        quote = Color(0xFF34D399),
        author = Color(0xFFFDBA74),
        subject = Color(0xFFECECEF),
        quoteLink = Color(0xFF7DD3FC),
        error = Color(0xFFF472B6),
        onError = Color(0xFF500724),
    )

/** The colors for a brightness mode; AMOLED applies only when [dark]. */
fun readerColors(
    dark: Boolean,
    amoled: Boolean,
    accentTheme: NextAccentTheme = NextAccentTheme.ORBIN,
): ReaderColors {
    val base =
        when {
            dark && amoled -> ReaderAmoledColors
            dark -> ReaderDarkColors
            else -> ReaderLightColors
        }
    return accentedReaderColors(base, dark, dark && amoled, accentTheme)
}

object ReaderSpace {
    /** Left and right screen edge inset. */
    val gutter: Dp = 16.dp

    /** Top and bottom padding of a catalog row. */
    val rowPadY: Dp = 12.dp

    /** Top and bottom padding of a post. */
    val postPadY: Dp = 12.dp

    /** Clover post text inset: the configured text size minus six dp. */
    val postInset: Dp = 8.dp

    val postInsetWide: Dp = 10.dp

    /** Clover's album container padding and individual cell margin. */
    val albumPadding: Dp = 1.dp

    val albumCellMargin: Dp = 1.dp

    /** Padding around the text block of a grid card. */
    val cardPad: Dp = 8.dp

    /** Between grid cards: the two adjacent 2dp card margins. */
    val gridGap: Dp = 4.dp

    /** Outside margin of catalog grid cards, matching Clover v3.0.2 on API 21+. */
    val gridOuter: Dp = 2.dp

    /** Thumbnail to text column. */
    val gap: Dp = 12.dp

    /** Text to pill, and between blocks inside a post. */
    val gapSmall: Dp = 8.dp

    /** Between title, meta line, and preview in a row. */
    val line: Dp = 4.dp
}

object ReaderSize {
    /** App bar height. */
    val appBar: Dp = 62.dp

    /** Clover toolbar leading navigation control width. */
    val appBarNavigation: Dp = 48.dp

    /** Clover toolbar regular action width. */
    val appBarAction: Dp = 48.dp

    /** Clover toolbar overflow control width. */
    val appBarOverflow: Dp = 48.dp

    /** Minimum size of anything that can be tapped. */
    val touchTarget: Dp = 48.dp

    /** App bar icon. */
    val icon: Dp = 19.dp

    /** Cell thumbnail width and height in a window that is not wide. */
    val thumbnail: Dp = 72.dp

    /** Cell thumbnail width and height in a wide window. */
    val thumbnailWide: Dp = 100.dp

    /** Minimum height of a cell that has a thumbnail. */
    val cellMin: Dp = 96.dp

    /** The same minimum in a wide window. */
    val cellMinWide: Dp = 124.dp

    /** Thread Files columns are the pane width over this, rounded down; also the minimum grid image height. */
    val gridCardMin: Dp = 120.dp

    /** Minimum height of a grid card's text block, so cards on one line stay level. */
    val gridCardText: Dp = 76.dp

    /** Catalog card elevation, matching Clover v3.0.2. */
    val cardElevation: Dp = 2.dp

    /** Thread footer that reports reply and image counts. */
    val threadStatus: Dp = 88.dp

    /** The album cell adds this metadata strip to its width to determine its height. */
    val albumDetails: Dp = 32.dp

    /** Reply count pill height. */
    val pillHeight: Dp = 36.dp

    /** Reply count pill minimum width. */
    val pillMinWidth: Dp = 48.dp

    /** Row and post divider thickness. */
    val hairline: Dp = 1.dp

    /** Refresh progress bar height. */
    val progress: Dp = 4.dp

    /** Maximum post text width in a wide pane. */
    val readingMax: Dp = 720.dp

    /** Maximum navigation drawer width. */
    val drawerMax: Dp = 336.dp

    /** Both window sides must reach this to count as wide. */
    val wideMin: Dp = 600.dp

    /** Wide windows at least this wide show two panes. */
    val twoPaneMinWidth: Dp = 800.dp

    /** Maximum catalog pane width in two panes. */
    val listPaneMax: Dp = 420.dp

    /** Divider between the two panes. */
    val paneDivider: Dp = 1.dp
}

/** Proportions the reader layout derives from its viewport. */
object ReaderRatio {
    /** A grid card's image height as a fraction of the grid viewport's height: two lines per screen. */
    val gridImageHeight: Float get() = 1f / 2f
}
