package com.orbin.uinext.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The reader design's tokens, copied from docs/design-system/README.md.
 *
 * That document is the source of truth. scripts/tests/test_design_spec.py reads its tables and
 * this file and fails when a value differs, so change the document first and this file with it.
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
    /** Screen, row, post, and card ground. */
    val background: Color,
    /** Secondary screen ground, thumbnail placeholder and skeleton bars. */
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
    /** Error state title. */
    val error: Color,
)

val ReaderLightColors =
    ReaderColors(
        appBar = Color(0xFFFFFFFF),
        statusBar = Color(0xFFFFFFFF),
        onAppBar = Color(0xFF1A1A1A),
        background = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF5F5F5),
        selected = Color(0xFFFFF5F6),
        ink = Color(0xDE000000),
        inkSecondary = Color(0x89000000),
        inkMuted = Color(0xFF646464),
        hairline = Color(0x1E000000),
        accent = Color(0xFFC73E4E),
        onAccent = Color(0xFFFFFFFF),
        quote = Color(0xFF2E7D32),
        author = Color(0xFF1A1A1A),
        subject = Color(0xFF1A1A1A),
        quoteLink = Color(0xFFC73E4E),
        error = Color(0xFFB3261E),
    )

val ReaderDarkColors =
    ReaderColors(
        appBar = Color(0xFF1C1C1E),
        statusBar = Color(0xFF000000),
        onAppBar = Color(0xFFFFFFFF),
        background = Color(0xFF1C1C1E),
        surfaceVariant = Color(0xFF2C2C2E),
        selected = Color(0xFF3D2A2D),
        ink = Color(0xFFFFFFFF),
        inkSecondary = Color(0xB2FFFFFF),
        inkMuted = Color(0xFFC5C8C6),
        hairline = Color(0x1EFFFFFF),
        accent = Color(0xFFFF8A96),
        onAccent = Color(0xFF3B0D14),
        quote = Color(0xFF81C784),
        author = Color(0xFFFFB0B8),
        subject = Color(0xFFFFB0B8),
        quoteLink = Color(0xFFFF9AA5),
        error = Color(0xFFF2B8B5),
    )

val ReaderAmoledColors =
    ReaderColors(
        appBar = Color(0xFF000000),
        statusBar = Color(0xFF000000),
        onAppBar = Color(0xFFFFFFFF),
        background = Color(0xFF000000),
        surfaceVariant = Color(0xFF212121),
        selected = Color(0xFF3D2A2D),
        ink = Color(0xFFFFFFFF),
        inkSecondary = Color(0xB2FFFFFF),
        inkMuted = Color(0xFFC5C8C6),
        hairline = Color(0x1EFFFFFF),
        accent = Color(0xFFFF8A96),
        onAccent = Color(0xFF3B0D14),
        quote = Color(0xFF81C784),
        author = Color(0xFFFFB0B8),
        subject = Color(0xFFFFB0B8),
        quoteLink = Color(0xFFFF9AA5),
        error = Color(0xFFF2B8B5),
    )

/** The colors for a brightness mode; AMOLED applies only when [dark]. */
fun readerColors(
    dark: Boolean,
    amoled: Boolean,
): ReaderColors =
    when {
        dark && amoled -> ReaderAmoledColors
        dark -> ReaderDarkColors
        else -> ReaderLightColors
    }

private fun readerType(
    size: Int,
    lineHeight: Int,
    weight: Int,
): TextStyle =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = FontWeight(weight),
    )

/** Type styles, in sp so text follows the reader's font scale. */
object ReaderType {
    /** App bar title. */
    val appBarTitle: TextStyle = readerType(20, 28, 500)

    /** Board line under a thread title. */
    val appBarSubtitle: TextStyle = readerType(14, 20, 400)

    /** Catalog row title. */
    val rowTitle: TextStyle = readerType(16, 22, 700)

    /** Post number, date, file details, backlinks. */
    val meta: TextStyle = readerType(12, 16, 400)

    /** Catalog row preview line. */
    val preview: TextStyle = readerType(14, 20, 400)

    /** Post body. */
    val body: TextStyle = readerType(15, 22, 400)

    /** Grid card title. */
    val cardTitle: TextStyle = readerType(14, 20, 700)

    /** Post author name. */
    val postAuthor: TextStyle = readerType(12, 16, 700)

    /** Clover's default post size; natural Roboto metrics supply its line spacing. */
    val postBody: TextStyle =
        TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, fontWeight = FontWeight.Normal)

    /** The sw600dp post size from Clover's tablet resource configuration. */
    val postBodyWide: TextStyle =
        TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, fontWeight = FontWeight.Normal)

    /** Post number, time and attachment metadata are four sp smaller than the body. */
    val postDetails: TextStyle =
        TextStyle(fontFamily = FontFamily.Default, fontSize = 10.sp, fontWeight = FontWeight.Normal)

    val postDetailsWide: TextStyle =
        TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, fontWeight = FontWeight.Normal)

    /** Reply count in the pill. */
    val pill: TextStyle = readerType(14, 20, 500)

    /** Empty and error state title. */
    val stateTitle: TextStyle = readerType(24, 32, 400)

    /** State helper text and the empty detail pane message. */
    val stateBody: TextStyle = readerType(16, 24, 400)

    /** Text buttons. */
    val label: TextStyle = readerType(14, 20, 500)
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
    val appBar: Dp = 56.dp

    /** Clover toolbar leading navigation control width. */
    val appBarNavigation: Dp = 56.dp

    /** Clover toolbar regular action width. */
    val appBarAction: Dp = 50.dp

    /** Clover toolbar overflow control width. */
    val appBarOverflow: Dp = 44.dp

    /** Minimum size of anything that can be tapped. */
    val touchTarget: Dp = 48.dp

    /** App bar icon. */
    val icon: Dp = 24.dp

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
    val readingMax: Dp = 640.dp

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

object ReaderRadius {
    /** Thumbnails and skeleton bars. */
    val small: Dp = 4.dp

    /** Grid card. */
    val card: Dp = 2.dp

    /** Reply count pill. */
    val pill: Dp = 18.dp
}

/** Proportions the reader layout derives from its viewport. */
object ReaderRatio {
    /** A grid card's image height as a fraction of the grid viewport's height: two lines per screen. */
    val gridImageHeight: Float get() = 1f / 2f
}
