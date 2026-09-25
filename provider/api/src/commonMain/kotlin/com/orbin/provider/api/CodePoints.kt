package com.orbin.provider.api

/** Highest Unicode code point, the multiplatform counterpart of `Character.MAX_CODE_POINT`. */
const val MAX_CODE_POINT: Int = 0x10FFFF

/** UTF-16 surrogate code units, which are never valid as scalar values on their own. */
val SURROGATE_CODE_UNITS: IntRange = 0xD800..0xDFFF

/**
 * [codePoint] as a string: one UTF-16 unit, or a surrogate pair above the Basic Multilingual Plane.
 * Behaves as `String(Character.toChars(codePoint))` for every valid code point, which
 * `CodePointsTest` checks exhaustively.
 */
fun codePointToString(codePoint: Int): String {
    require(codePoint in 0..MAX_CODE_POINT) { "Not a Unicode code point: $codePoint" }
    if (codePoint < SUPPLEMENTARY_START) return codePoint.toChar().toString()
    val offset = codePoint - SUPPLEMENTARY_START
    val high = (HIGH_SURROGATE_START + (offset shr SURROGATE_SHIFT)).toChar()
    val low = (LOW_SURROGATE_START + (offset and LOW_TEN_BITS)).toChar()
    return charArrayOf(high, low).concatToString()
}

private const val SUPPLEMENTARY_START = 0x10000
private const val HIGH_SURROGATE_START = 0xD800
private const val LOW_SURROGATE_START = 0xDC00
private const val SURROGATE_SHIFT = 10
private const val LOW_TEN_BITS = 0x3FF
