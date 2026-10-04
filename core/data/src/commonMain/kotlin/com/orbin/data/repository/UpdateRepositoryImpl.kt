package com.orbin.data.repository

import com.orbin.core.common.result.DataError
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.UpdateStatus
import com.orbin.domain.repository.UpdateRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val LATEST_RELEASE_URL = "https://api.github.com/repos/Defuuls/Orbin/releases/latest"
private const val RELEASE_DOWNLOAD_PREFIX = "https://github.com/Defuuls/Orbin/releases/download/"

class UpdateRepositoryImpl(
    private val client: HttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UpdateRepository {
    override suspend fun checkForUpdate(currentVersionName: String): OrbinResult<UpdateStatus> =
        withContext(ioDispatcher) {
            try {
                val response =
                    client.get(LATEST_RELEASE_URL) {
                        header("Accept", "application/vnd.github+json")
                    }
                if (!response.status.isSuccess()) {
                    throw HttpStatusException(response.status.value)
                }
                val body = response.bodyAsText()
                OrbinResult.Success(parseLatestRelease(body, currentVersionName))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OrbinResult.Failure(e.toDataError())
            }
        }

    private fun Throwable.toDataError(): DataError =
        when (this) {
            is HttpStatusException -> DataError.Server(code, this)
            is SerializationException -> DataError.Parse(this)
            else -> {
                val msg = message.orEmpty().lowercase()
                if ("timeout" in msg) {
                    DataError.Timeout(this)
                } else if ("network" in msg ||
                    "connect" in msg ||
                    "unresolved" in msg ||
                    "offline" in msg ||
                    "unknownhost" in msg
                ) {
                    DataError.Offline(this)
                } else {
                    DataError.Unknown(this)
                }
            }
        }
}

class HttpStatusException(
    val code: Int,
) : Exception("GitHub returned HTTP $code")

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

private val updateJson =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

fun parseLatestRelease(
    json: String,
    currentVersionName: String,
): UpdateStatus {
    val release = updateJson.decodeFromString<GitHubRelease>(json)
    val tag = release.tagName
    return if (releaseNumber(tag) > releaseNumber(currentVersionName)) {
        val downloads =
            release.assets
                .associate { it.name to it.browserDownloadUrl }
                .filterValues(::isReleaseDownload)
        val apk = downloads.keys.firstOrNull { it.startsWith("orbin-") && it.endsWith(".apk") }
        UpdateStatus.Available(
            tag = tag,
            name = release.name?.takeIf { it.isNotBlank() } ?: tag,
            url = release.htmlUrl,
            apkUrl = apk?.let(downloads::get),
            checksumUrl = apk?.let { downloads["$it.sha256"] },
        )
    } else {
        UpdateStatus.UpToDate
    }
}

private fun releaseNumber(tag: String): Int = tag.removePrefix("v").substringBefore('-').toIntOrNull() ?: 0

private fun isReleaseDownload(url: String): Boolean = url.startsWith(RELEASE_DOWNLOAD_PREFIX)
