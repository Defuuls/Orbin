package com.orbin.data.repository

import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.core.common.result.DataError
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.UpdateStatus
import com.orbin.domain.repository.UpdateRepository
import com.orbin.network.di.BaseOkHttp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

private const val LATEST_RELEASE_URL = "https://api.github.com/repos/Defuuls/Orbin/releases/latest"

/**
 * Reads the newest published release from the GitHub API.
 *
 * Only the release *metadata* is fetched; the APK is never downloaded in-app. Sideloading a
 * signed build is the user's decision to make deliberately, on the release page, where the
 * checksums are — an in-app installer would be a materially larger trust surface for the sake of
 * skipping two taps.
 */
class UpdateRepositoryImpl
    @Inject
    constructor(
        @BaseOkHttp private val client: OkHttpClient,
        @Dispatcher(OrbinDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    ) : UpdateRepository {
        override suspend fun checkForUpdate(currentVersionName: String): OrbinResult<UpdateStatus> =
            withContext(ioDispatcher) {
                runCatching {
                    val request =
                        Request
                            .Builder()
                            .url(LATEST_RELEASE_URL)
                            .header("Accept", "application/vnd.github+json")
                            .build()

                    val body =
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) throw HttpStatusException(response.code)
                            response.body.string()
                        }

                    parseLatestRelease(body, currentVersionName)
                }.fold(
                    onSuccess = { OrbinResult.Success(it) },
                    onFailure = { OrbinResult.Failure(it.toDataError()) },
                )
            }

        /**
         * The check is user-initiated, so its failure is shown verbatim in a snackbar. Categorising
         * it is what makes that message actionable — "No network connection" tells the user what to
         * do, and [DataError.Unknown]'s "Something went wrong" does not.
         */
        private fun Throwable.toDataError(): DataError =
            when (this) {
                is HttpStatusException -> DataError.Server(code, this)
                is SocketTimeoutException -> DataError.Timeout(this)
                is UnknownHostException, is ConnectException -> DataError.Offline(this)
                is JSONException -> DataError.Parse(this)
                is IOException -> DataError.Offline(this)
                else -> DataError.Unknown(this)
            }
    }

/** Carries the status code so it survives into [DataError.Server]. */
private class HttpStatusException(
    val code: Int,
) : IOException("GitHub returned HTTP $code")

/**
 * Turns a GitHub `releases/latest` payload into an [UpdateStatus] relative to [currentVersionName].
 *
 * Separate from the repository so the comparison can be tested without a network stack — it is the
 * part with a real chance of being wrong.
 */
internal fun parseLatestRelease(
    json: String,
    currentVersionName: String,
): UpdateStatus {
    val release = JSONObject(json)
    val tag = release.getString("tag_name")
    return if (releaseNumber(tag) > releaseNumber(currentVersionName)) {
        UpdateStatus.Available(
            tag = tag,
            name = release.optString("name").takeIf { it.isNotBlank() } ?: tag,
            url = release.getString("html_url"),
        )
    } else {
        UpdateStatus.UpToDate
    }
}

/**
 * Tags are `v<number>-<Codename>`, so the leading number is the only ordered part — codenames are
 * stars, not versions. Comparing tags as strings would sort "v9" after "v61", and comparing
 * codenames would be meaningless. An unreadable tag yields 0, which reports "up to date" rather
 * than nagging the user about a release that may not exist.
 */
private fun releaseNumber(tag: String): Int = tag.removePrefix("v").substringBefore('-').toIntOrNull() ?: 0
