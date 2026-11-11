package com.orbin.core.model

/**
 * What kind of device the app runs on, for the choices only some devices offer.
 *
 * It is the device, not the current window: a foldable is a [FOLDABLE] folded or open, and
 * [feedColumns] reads the window's width to tell the two apart.
 */
enum class FormFactor(
    /** The most feed columns this device may choose on its wide screen; 1 means no choice. */
    val maxFeedColumns: Int,
) {
    PHONE(1),
    FOLDABLE(FOLDABLE_MAX_FEED_COLUMNS),
    TABLET(TABLET_MAX_FEED_COLUMNS),
}

private const val FOLDABLE_MAX_FEED_COLUMNS = 3
private const val TABLET_MAX_FEED_COLUMNS = 4

/**
 * A window is wide, and takes the reader's column choices, only when both its sides are at least
 * this long: Android's own line for a tablet's screen.
 *
 * Both sides, not just the width, because a foldable's cover screen turned sideways is wide but
 * short. A Galaxy Z Fold8's 10:16 cover is about 751 x 475dp in landscape and a Fold8 Ultra's
 * 21:9 cover about 960 x 411dp; neither is the unfolded screen the column setting is for. Their
 * inner screens, a 4:3 Fold8 (about 933 x 696dp) and a near-square Ultra (about 859 x 954dp), are
 * wide either way up.
 */
const val WIDE_SCREEN_MIN_DP = 600

/** Whether a [windowWidthDp] by [windowHeightDp] window is wide: see [WIDE_SCREEN_MIN_DP]. */
fun isWideWindow(
    windowWidthDp: Int,
    windowHeightDp: Int,
): Boolean = windowWidthDp >= WIDE_SCREEN_MIN_DP && windowHeightDp >= WIDE_SCREEN_MIN_DP

/**
 * A wide window at least this wide shows a board's catalog and the open thread side by side. Low
 * enough for both unfolded Fold8 screens: the Fold8's held landscape and the Ultra's either way up.
 */
const val TWO_PANE_MIN_WIDTH_DP = 800

/** Whether a board's catalog and its open thread share the window rather than taking turns. */
fun showsTwoPanes(
    windowWidthDp: Int,
    windowHeightDp: Int,
): Boolean = isWideWindow(windowWidthDp, windowHeightDp) && windowWidthDp >= TWO_PANE_MIN_WIDTH_DP

/** Exact pane widths in dp; together they occupy the whole two-pane window. */
data class ReaderPaneWidths(
    val catalogDp: Int,
    val dividerDp: Int,
    val threadDp: Int,
)

/**
 * The catalog gets five twelfths of the width after the divider, rounded down and capped at
 * 420dp; the thread gets the remainder. Call only for windows eligible for two panes: widths
 * below [TWO_PANE_MIN_WIDTH_DP] are rejected, and the caller still checks the window height.
 */
fun readerPaneWidths(windowWidthDp: Int): ReaderPaneWidths {
    require(windowWidthDp >= TWO_PANE_MIN_WIDTH_DP) { "Two panes require a window at least 800dp wide." }
    val catalog =
        ((windowWidthDp.toLong() - PANE_DIVIDER_DP) * CATALOG_PANE_NUMERATOR / CATALOG_PANE_DENOMINATOR)
            .coerceAtMost(CATALOG_PANE_MAX_DP.toLong())
            .toInt()
    return ReaderPaneWidths(
        catalogDp = catalog,
        dividerDp = PANE_DIVIDER_DP,
        threadDp = windowWidthDp - PANE_DIVIDER_DP - catalog,
    )
}

private const val PANE_DIVIDER_DP = 1
private const val CATALOG_PANE_NUMERATOR = 5
private const val CATALOG_PANE_DENOMINATOR = 12
private const val CATALOG_PANE_MAX_DP = 420

/**
 * How many columns the feed shows: one on a window that is not wide ([isWideWindow]) and on a
 * phone; otherwise the reader's choice for this kind of device, within what it allows.
 */
fun feedColumns(
    formFactor: FormFactor,
    windowWidthDp: Int,
    windowHeightDp: Int,
    settings: AppSettings,
): Int {
    if (!isWideWindow(windowWidthDp, windowHeightDp)) return 1
    return settings.feedColumnsFor(formFactor).coerceIn(1, formFactor.maxFeedColumns)
}

/** The reader's saved column count for [formFactor]'s wide screen. */
fun AppSettings.feedColumnsFor(formFactor: FormFactor): Int =
    when (formFactor) {
        FormFactor.PHONE -> 1
        FormFactor.FOLDABLE -> unfoldedFeedColumns
        FormFactor.TABLET -> tabletFeedColumns
    }

/** The fewest board catalog columns a tablet may choose. */
const val TABLET_MIN_CATALOG_COLUMNS = 2

/** The most board catalog columns a tablet may choose. */
const val TABLET_MAX_CATALOG_COLUMNS = 8

/**
 * How many columns a board catalog shows: the reader's choice on a tablet's wide window, within
 * what it allows; null elsewhere (a phone, a foldable, split screen), where the catalog fits as
 * many tiles as the window takes.
 */
fun catalogColumns(
    formFactor: FormFactor,
    windowWidthDp: Int,
    windowHeightDp: Int,
    settings: AppSettings,
): Int? {
    if (formFactor != FormFactor.TABLET || !isWideWindow(windowWidthDp, windowHeightDp)) return null
    return settings.tabletCatalogColumns.coerceIn(TABLET_MIN_CATALOG_COLUMNS, TABLET_MAX_CATALOG_COLUMNS)
}
