package com.orbin.provider.lynxchan.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
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

    override suspend fun catalog(board: String): List<LynxChanCatalogThread> =
        get(ListSerializer(LynxChanCatalogThread.serializer()), listOf(board, "catalog.json"))

    override suspend fun thread(
        board: String,
        threadId: Long,
    ): LynxChanThreadResponse = get(LynxChanThreadResponse.serializer(), listOf(board, "res", "$threadId.json"))

    private suspend fun <T> get(
        deserializer: DeserializationStrategy<T>,
        path: List<String>,
        query: Map<String, String> = emptyMap(),
    ): T {
        val body =
            client
                .get {
                    expectSuccess = true
                    url {
                        takeFrom(baseUrl)
                        appendPathSegments(path)
                    }
                    query.forEach { (name, value) -> parameter(name, value) }
                }.bodyAsText()
        return json.decodeFromString(deserializer, body)
    }
}
