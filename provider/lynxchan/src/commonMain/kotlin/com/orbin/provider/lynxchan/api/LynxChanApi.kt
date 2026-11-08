package com.orbin.provider.lynxchan.api

import com.orbin.provider.api.ProviderException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The LynxChan JSON API. The base URL is supplied per provider instance (see
 * [com.orbin.provider.lynxchan.LynxChanSite]); paths are relative to it.
 */
interface LynxChanApi {
    /** Site-wide board list. */
    suspend fun boards(): LynxChanBoardsResponse

    /** Full catalog for a board, as a flat array of thread previews. */
    suspend fun catalog(board: String): List<LynxChanCatalogThread>

    /** A single thread by its OP post number. */
    suspend fun thread(
        board: String,
        threadId: Long,
    ): LynxChanThreadResponse
}

/**
 * [LynxChanApi] over Ktor, so the same code runs on Android (OkHttp engine) and iOS (Darwin engine).
 *
 * Non-2xx responses surface as Ktor's `ResponseException` and transport failures as `IOException`;
 * [com.orbin.provider.lynxchan.LynxChanProvider] maps both to the provider contract.
 */
class KtorLynxChanApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val json: Json,
) : LynxChanApi {
    override suspend fun boards(): LynxChanBoardsResponse =
        get(LynxChanBoardsResponse.serializer(), listOf("boards.js"), query = mapOf("json" to "1"))

    override suspend fun catalog(board: String): List<LynxChanCatalogThread> {
        requireSafeSegment(board)
        return get(ListSerializer(LynxChanCatalogThread.serializer()), listOf(board, "catalog.json"))
    }

    override suspend fun thread(
        board: String,
        threadId: Long,
    ): LynxChanThreadResponse {
        requireSafeSegment(board)
        return get(LynxChanThreadResponse.serializer(), listOf(board, "res", "$threadId.json"))
    }

    private suspend fun <T> get(
        deserializer: DeserializationStrategy<T>,
        path: List<String>,
        query: Map<String, String> = emptyMap(),
    ): T =
        client.prepareGet {
            // Keep both success and error bodies out of Ktor's unbounded saved-response path.
            expectSuccess = false
            url {
                takeFrom(baseUrl)
                appendPathSegments(path)
            }
            query.forEach { (name, value) -> parameter(name, value) }
        }.execute { response ->
            if (!response.status.isSuccess()) throw ResponseException(response, "")
            val channel = response.bodyAsChannel()
            val bytes = channel.readBuffer(MAX_JSON_BYTES.toLong() + 1).readByteArray()
            if (bytes.size > MAX_JSON_BYTES) {
                channel.cancel(null)
                throw ProviderException.Parse("Response exceeded maximum allowed size")
            }
            json.decodeFromString(deserializer, bytes.decodeToString())
        }

    private fun requireSafeSegment(board: String) {
        require(board.isNotEmpty() && board.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
            "Invalid board code"
        }
    }

    private companion object {
        const val MAX_JSON_BYTES = 16 * 1024 * 1024
    }
}
