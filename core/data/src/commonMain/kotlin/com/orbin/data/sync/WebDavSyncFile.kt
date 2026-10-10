package com.orbin.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Where the sync file lives: a WebDAV folder, and the account that may write to it. */
data class SyncAccount(
    val folderUrl: String,
    val username: String,
    val password: String,
) {
    /** The file both apps read and write, inside the folder. */
    val fileUrl: String get() = folderUrl.trimEnd('/') + "/" + SYNC_FILE_NAME

    /** Only https: the file holds reading history, and the login goes with every request. */
    val isUsable: Boolean
        get() = folderUrl.startsWith("https://", ignoreCase = true) && username.isNotBlank() && password.isNotEmpty()
}

/** The sync file as last read: its text and the version tag the server gave it. */
data class RemoteSyncFile(
    val text: String,
    val etag: String?,
)

/** Why a sync could not finish, in words for the settings row. */
class SyncException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Reads and writes the one sync file over WebDAV: a GET and a conditional PUT, which every WebDAV
 * server (Nextcloud, ownCloud, Synology, Apache's mod_dav…) accepts. The PUT only lands if the file
 * is still the version read, so two devices syncing at once cannot overwrite each other.
 */
class WebDavSyncFile(
    private val client: HttpClient,
) {
    /** The file, or null if there is none yet. */
    suspend fun read(account: SyncAccount): RemoteSyncFile? {
        val response = request { client.get(account.fileUrl) { authorize(account) } }
        return when {
            response.status == HttpStatusCode.NotFound -> null
            response.status.isSuccess() -> RemoteSyncFile(response.bodyAsText(), response.headers[HttpHeaders.ETag])
            else -> throw response.failure()
        }
    }

    /**
     * Writes [text] if the file is still at [etag] (or, with no [etag], still absent). False when
     * another device wrote first, so the caller can read again and merge.
     */
    suspend fun write(
        account: SyncAccount,
        text: String,
        etag: String?,
    ): Boolean {
        val response =
            request {
                client.put(account.fileUrl) {
                    authorize(account)
                    if (etag != null) header(HttpHeaders.IfMatch, etag) else header(HttpHeaders.IfNoneMatch, "*")
                    contentType(ContentType.Application.Json)
                    setBody(text)
                }
            }
        return when {
            response.status == HttpStatusCode.PreconditionFailed -> false
            response.status.isSuccess() -> true
            response.status == HttpStatusCode.Conflict -> throw SyncException("The sync folder doesn't exist")
            else -> throw response.failure()
        }
    }

    private suspend fun request(block: suspend () -> HttpResponse): HttpResponse =
        try {
            block()
        } catch (
            // Ktor and the engines throw their own unrelated types; any of them is an unreachable server.
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            throw SyncException("Couldn't reach the sync server", error)
        }

    @OptIn(ExperimentalEncodingApi::class)
    private fun io.ktor.client.request.HttpRequestBuilder.authorize(account: SyncAccount) {
        val credentials = Base64.encode("${account.username}:${account.password}".encodeToByteArray())
        header(HttpHeaders.Authorization, "Basic $credentials")
    }

    private fun HttpResponse.failure(): SyncException =
        when (status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> SyncException("The sync server refused the login")
            else -> SyncException("The sync server answered ${status.value}")
        }
}

const val SYNC_FILE_NAME = "orbin-sync.json"
