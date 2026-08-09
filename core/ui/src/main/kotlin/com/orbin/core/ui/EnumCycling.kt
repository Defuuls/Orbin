package com.orbin.core.ui

/**
 * The next value of this enum, wrapping from the last value back to the first.
 *
 * Used by the layout-mode and thumbnail-size toggles that cycle through their options one tap at
 * a time (board, thread, and subscribed-feed screens each have one of these).
 */
inline fun <reified T : Enum<T>> T.next(): T {
    val values = enumValues<T>()
    return values[(values.indexOf(this) + 1) % values.size]
}
