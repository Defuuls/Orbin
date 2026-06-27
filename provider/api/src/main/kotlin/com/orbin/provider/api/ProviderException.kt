package com.orbin.provider.api

/**
 * Typed failures a provider may raise. Providers throw these from their suspend functions; the
 * data layer catches them and maps to the app's `Result` type. Using a sealed hierarchy means
 * callers can react meaningfully (retry, show "thread deleted", etc.) instead of parsing strings.
 */
sealed class ProviderException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** No network / host unreachable / timeout. Generally retryable. */
    class Network(
        message: String,
        cause: Throwable? = null,
    ) : ProviderException(message, cause)

    /** Server responded with an error status. */
    class Http(
        val code: Int,
        message: String,
        cause: Throwable? = null,
    ) : ProviderException(message, cause)

    /** The requested resource no longer exists (404 on a thread → it was pruned/deleted). */
    class NotFound(
        message: String,
        cause: Throwable? = null,
    ) : ProviderException(message, cause)

    /** The response could not be parsed into the expected shape. */
    class Parse(
        message: String,
        cause: Throwable? = null,
    ) : ProviderException(message, cause)

    /** The provider was asked to do something it does not support. */
    class Unsupported(
        operation: String,
    ) : ProviderException("Operation not supported by this provider: $operation")

    /** Rate limited by the upstream service; [retryAfterSeconds] is honored when present. */
    class RateLimited(
        val retryAfterSeconds: Long? = null,
    ) : ProviderException("Rate limited" + (retryAfterSeconds?.let { " (retry after ${it}s)" } ?: ""))
}
