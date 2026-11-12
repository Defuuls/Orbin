package com.orbin.feature.board

import com.orbin.provider.api.ProviderException
import com.orbin.uinext.ReaderFailureCause
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
    SERVER,
    OTHER,
}

internal fun Throwable.catalogFailure(): CatalogFailure {
    val seen = mutableSetOf<Throwable>()
    val chain = generateSequence(this) { it.cause }.takeWhile(seen::add).toList()
    val text = chain.mapNotNull { it.message }.joinToString(" ")
    val status =
        chain.filterIsInstance<ProviderException.Http>().firstOrNull()?.code
            ?: HTTP_STATUS
                .find(text)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
    return when {
        chain.any { it is ProviderException.NotFound } ||
            status == HTTP_NOT_FOUND ||
            text.contains("Not found", ignoreCase = true) -> CatalogFailure.GONE
        chain.any { it is ProviderException.RateLimited } || status == HTTP_RATE_LIMITED -> CatalogFailure.RATE_LIMITED
        status != null && status in HTTP_SERVER_ERRORS -> CatalogFailure.SERVER
        chain.any { it is UnknownHostException || it is ConnectException } -> CatalogFailure.OFFLINE
        chain.any { it is SocketTimeoutException } -> CatalogFailure.SLOW
        chain.any { it is ProviderException.Network || it is IOException } -> CatalogFailure.OFFLINE
        else -> CatalogFailure.OTHER
    }
}

internal val CatalogFailure.readerCause: ReaderFailureCause
    get() =
        when (this) {
            CatalogFailure.OFFLINE, CatalogFailure.SLOW -> ReaderFailureCause.NETWORK
            CatalogFailure.GONE -> ReaderFailureCause.NOT_FOUND
            CatalogFailure.RATE_LIMITED, CatalogFailure.SERVER -> ReaderFailureCause.SERVER
            CatalogFailure.OTHER -> ReaderFailureCause.UNKNOWN
        }

internal val CatalogFailure.messageRes: Int
    get() =
        when (this) {
            CatalogFailure.OFFLINE -> R.string.board_error_offline
            CatalogFailure.SLOW -> R.string.board_error_slow
            CatalogFailure.RATE_LIMITED -> R.string.board_error_rate_limited
            CatalogFailure.GONE -> R.string.board_error_gone
            CatalogFailure.SERVER -> R.string.board_error_server
            CatalogFailure.OTHER -> R.string.board_load_error
        }

private val HTTP_STATUS = Regex("\\bHTTP\\s+(\\d{3})\\b", RegexOption.IGNORE_CASE)
private const val HTTP_NOT_FOUND = 404
private const val HTTP_RATE_LIMITED = 429
private val HTTP_SERVER_ERRORS = 500..599
