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
    /** App bar and status bar ground. */
    val appBar: Color,
    /** App bar title and icons. */
    val onAppBar: Color,
    /** Screen, row, post, and card ground. */
    val background: Color,
    /** Thumbnail placeholder, count pill, skeleton bars. */
    val surfaceVariant: Color,
    /** Open thread's row in the two-pane catalog. */
    val selected: Color,
    /** Titles, post bodies, pill count. */
    val ink: Color,
    /** Post numbers, dates, file details, helper text. */
    val inkMuted: Color,
    /** Row, post, and pane dividers; card outline. */
    val hairline: Color,
    /** Progress bar and text buttons. */
    val accent: Color,
    /** Text on an accent-filled control. */
    val onAccent: Color,
    /** Quoted (greentext) lines. */
    val quote: Color,
    /** Post author name. */
    val author: Color,
    /** Post reference links and backlinks. */
    val quoteLink: Color,
    /** Error state title. */
    val error: Color,
)

val ReaderLightColors =
    ReaderColors(
        appBar = Color(0xFF2E7D32),
        onAppBar = Color(0xFFFFFFFF),
        background = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF1F3F4),
        selected = Color(0xFFE8F5E9),
        ink = Color(0xFF1F1F1F),
        inkMuted = Color(0xFF5F6368),
        hairline = Color(0xFFDADCE0),
        accent = Color(0xFF00796B),
        onAccent = Color(0xFFFFFFFF),
        quote = Color(0xFF2E7D32),
        author = Color(0xFF117743),
        quoteLink = Color(0xFF0B57D0),
        error = Color(0xFFB3261E),
    )

val ReaderDarkColors =
    ReaderColors(
        appBar = Color(0xFF1E1E1E),
        onAppBar = Color(0xFFE8EAED),
        background = Color(0xFF121212),
        surfaceVariant = Color(0xFF2A2A2A),
        selected = Color(0xFF1B3A1F),
        ink = Color(0xFFE8EAED),
        inkMuted = Color(0xFFBDC1C6),
        hairline = Color(0xFF3C4043),
        accent = Color(0xFF80CBC4),
        onAccent = Color(0xFF003731),
        quote = Color(0xFF81C784),
        author = Color(0xFF81C784),
        quoteLink = Color(0xFFA8C7FA),
        error = Color(0xFFF2B8B5),
    )

val ReaderAmoledColors =
    ReaderColors(
        appBar = Color(0xFF000000),
        onAppBar = Color(0xFFE8EAED),
        background = Color(0xFF000000),
        surfaceVariant = Color(0xFF1A1A1A),
        selected = Color(0xFF12261A),
        ink = Color(0xFFE8EAED),
        inkMuted = Color(0xFFBDC1C6),
        hairline = Color(0xFF2A2A2A),
        accent = Color(0xFF80CBC4),
        onAccent = Color(0xFF003731),
        quote = Color(0xFF81C784),
        author = Color(0xFF81C784),
        quoteLink = Color(0xFFA8C7FA),
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

    /** Padding around the text block of a grid card. */
    val cardPad: Dp = 8.dp

    /** Between grid cards and around the grid. */
    val gridGap: Dp = 4.dp

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

    /** Grid column count is the pane width divided by this, rounded down. */
    val gridCardMin: Dp = 120.dp

    /** Height of the text block under a grid card image. */
    val gridCardText: Dp = 76.dp

    /** Thread footer that reports reply and image counts. */
    val threadStatus: Dp = 88.dp

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
    val drawerMax: Dp = 320.dp

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
