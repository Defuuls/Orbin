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
 * Reads the newest published release from the GitHub API: its tag, and where its signed APK and
 * that APK's checksum are. Downloading and installing is [com.orbin.data.update.AppUpdaterImpl]'s
 * job, which will not install an APK that fails either the checksum or the signing-key check.
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
        val downloads = release.releaseDownloads()
        val apk = downloads.keys.firstOrNull { it.startsWith("orbin-") && it.endsWith(".apk") }
        UpdateStatus.Available(
            tag = tag,
            name = release.optString("name").takeIf { it.isNotBlank() } ?: tag,
            url = release.getString("html_url"),
            apkUrl = apk?.let(downloads::get),
            checksumUrl = apk?.let { downloads["$it.sha256"] },
        )
    } else {
        UpdateStatus.UpToDate
    }
}

/**
 * Tags are `v<number>-<Codename>`, so the leading number is the only ordered part — a codename is a
 * label drawn from whatever theme the project is on (stars up to v90, pasta from v91), never a
 * version component. Comparing tags as strings would sort "v9" after "v61", and comparing codenames
 * would be meaningless in any theme. An unreadable tag yields 0, which reports "up to date" rather
 * than nagging the user about a release that may not exist.
 */
private fun releaseNumber(tag: String): Int = tag.removePrefix("v").substringBefore('-').toIntOrNull() ?: 0

/** Each asset's file name to its download URL, keeping only this repository's release downloads. */
private fun JSONObject.releaseDownloads(): Map<String, String> {
    val assets = optJSONArray("assets") ?: return emptyMap()
    return (0 until assets.length())
        .map(assets::getJSONObject)
        .associate { it.optString("name") to it.optString("browser_download_url") }
        .filterValues(::isReleaseDownload)
}

/**
 * Only this repository's release downloads, over HTTPS, are fetched. The API response is already
 * trusted to name the release; this keeps a malformed or tampered asset list from pointing the
 * downloader anywhere else.
 */
private fun isReleaseDownload(url: String): Boolean = url.startsWith(RELEASE_DOWNLOAD_PREFIX)

private const val RELEASE_DOWNLOAD_PREFIX = "https://github.com/Defuuls/Orbin/releases/download/"
