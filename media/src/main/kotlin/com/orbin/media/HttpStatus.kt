package com.orbin.media

import androidx.media3.datasource.HttpDataSource
import coil3.network.HttpException

/**
 * The HTTP status a failed media load carried, whether ExoPlayer (video) or Coil (images) reported
 * it, or null when the load never got a response. Read from the exception types themselves:
 * matching on message text misfires because media file names are timestamps full of digits.
 */
internal fun Throwable.httpStatusCode(): Int? =
    generateSequence(this as Throwable?) { it.cause }.firstNotNullOfOrNull { cause ->
        when (cause) {
            is HttpDataSource.InvalidResponseCodeException -> cause.responseCode
            is HttpException -> cause.response.code
            else -> null
        }
    }

internal fun Throwable.hasHttpStatus(statusCode: Int): Boolean = httpStatusCode() == statusCode
