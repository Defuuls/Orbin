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

/** Below this window width the feed is one column on any device: a folded phone, split screen. */
const val WIDE_SCREEN_MIN_DP = 600

/**
 * How many columns the feed shows: one on any window narrower than [WIDE_SCREEN_MIN_DP], and on a
 * phone; otherwise the reader's choice for this kind of device, within what it allows.
 */
fun feedColumns(
    formFactor: FormFactor,
    windowWidthDp: Int,
    settings: AppSettings,
): Int {
    if (windowWidthDp < WIDE_SCREEN_MIN_DP) return 1
    return settings.feedColumnsFor(formFactor).coerceIn(1, formFactor.maxFeedColumns)
}

/** The reader's saved column count for [formFactor]'s wide screen. */
fun AppSettings.feedColumnsFor(formFactor: FormFactor): Int =
    when (formFactor) {
        FormFactor.PHONE -> 1
        FormFactor.FOLDABLE -> unfoldedFeedColumns
        FormFactor.TABLET -> tabletFeedColumns
    }
