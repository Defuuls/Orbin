package com.orbin.feature.board

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Why a catalog failed to load, in words, not an exception's message. */
internal enum class CatalogFailure {
    OFFLINE,
    SLOW,
    RATE_LIMITED,
    GONE,
    OTHER,
}

internal fun Throwable.catalogFailure(): CatalogFailure {
    val chain = generateSequence(this) { it.cause }.toList()
    val text = chain.mapNotNull { it.message }.joinToString(" ")
    return when {
        chain.any { it is UnknownHostException || it is ConnectException } -> CatalogFailure.OFFLINE
        chain.any { it is SocketTimeoutException } -> CatalogFailure.SLOW
        "429" in text -> CatalogFailure.RATE_LIMITED
        "404" in text || "Not found" in text -> CatalogFailure.GONE
        chain.any { it is IOException } -> CatalogFailure.OFFLINE
        else -> CatalogFailure.OTHER
    }
}

internal val CatalogFailure.messageRes: Int
    get() =
        when (this) {
            CatalogFailure.OFFLINE -> R.string.board_error_offline
            CatalogFailure.SLOW -> R.string.board_error_slow
            CatalogFailure.RATE_LIMITED -> R.string.board_error_rate_limited
            CatalogFailure.GONE -> R.string.board_error_gone
            CatalogFailure.OTHER -> R.string.board_load_error
        }
