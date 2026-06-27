package com.orbin.data.util

import com.orbin.core.common.result.DataError
import com.orbin.core.common.result.OrbinResult
import com.orbin.provider.api.ProviderException

/**
 * Runs a provider call and normalizes its outcome into an [OrbinResult], translating the typed
 * [ProviderException] hierarchy into the app's [DataError] categories. This is the single place
 * provider failures cross into the rest of the app.
 */
internal suspend fun <T> runCatchingProvider(block: suspend () -> T): OrbinResult<T> =
    try {
        OrbinResult.Success(block())
    } catch (e: ProviderException) {
        OrbinResult.Failure(e.toDataError())
    }

internal fun ProviderException.toDataError(): DataError =
    when (this) {
        is ProviderException.Network -> DataError.Offline(this)
        is ProviderException.Http -> DataError.Server(code, this)
        is ProviderException.NotFound -> DataError.NotFound(message ?: "Not found", this)
        is ProviderException.Parse -> DataError.Parse(this)
        is ProviderException.RateLimited -> DataError.RateLimited(retryAfterSeconds)
        is ProviderException.Unsupported -> DataError.Unknown(this)
    }
