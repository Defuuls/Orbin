package com.orbin.core.common.result

/**
 * A lightweight, explicit result type used across data and domain boundaries instead of throwing.
 * Unlike Kotlin's [kotlin.Result], it carries a typed [DataError] so the UI can branch on the
 * failure category (offline vs. not-found vs. parse) without inspecting exception classes.
 */
sealed interface OrbinResult<out T> {

    data class Success<out T>(val data: T) : OrbinResult<T>

    data class Failure(val error: DataError) : OrbinResult<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.data
}

/** Categorized, user-presentable error. Mapped from provider/transport exceptions by the data layer. */
sealed class DataError(val message: String, val cause: Throwable? = null) {
    class Offline(cause: Throwable? = null) : DataError("No network connection", cause)
    class Timeout(cause: Throwable? = null) : DataError("The request timed out", cause)
    class NotFound(detail: String = "Not found", cause: Throwable? = null) : DataError(detail, cause)
    class RateLimited(val retryAfterSeconds: Long? = null) : DataError("Too many requests")
    class Server(val code: Int, cause: Throwable? = null) : DataError("Server error ($code)", cause)
    class Parse(cause: Throwable? = null) : DataError("Could not read the response", cause)
    class Unknown(cause: Throwable? = null) : DataError("Something went wrong", cause)
}

inline fun <T, R> OrbinResult<T>.map(transform: (T) -> R): OrbinResult<R> = when (this) {
    is OrbinResult.Success -> OrbinResult.Success(transform(data))
    is OrbinResult.Failure -> this
}

inline fun <T, R> OrbinResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (DataError) -> R,
): R = when (this) {
    is OrbinResult.Success -> onSuccess(data)
    is OrbinResult.Failure -> onFailure(error)
}

inline fun <T> OrbinResult<T>.onSuccess(action: (T) -> Unit): OrbinResult<T> = apply {
    if (this is OrbinResult.Success) action(data)
}

inline fun <T> OrbinResult<T>.onFailure(action: (DataError) -> Unit): OrbinResult<T> = apply {
    if (this is OrbinResult.Failure) action(error)
}

fun <T> T.asSuccess(): OrbinResult<T> = OrbinResult.Success(this)
