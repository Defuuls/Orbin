package com.orbin.core.reader

/**
 * The one way a board is written for a reader: its code between single slashes, `/g/`.
 *
 * Accepts the code with or without slashes, so callers never wrap a value that is already
 * wrapped (the bug that showed `//p//` on grid cards). Screens in `ui-next` render this label
 * verbatim and never add slashes of their own.
 */
fun boardLabel(board: String): String {
    val code = board.trim().trim('/')
    return if (code.isEmpty()) "" else "/$code/"
}
