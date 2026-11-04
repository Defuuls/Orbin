package com.orbin.provider.vichan.api

import com.orbin.provider.api.ProviderException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The vichan/4chan JSON API. The base URL is supplied per provider instance (see
 * [com.orbin.provider.vichan.VichanSite]); paths are relative to it.
 */
interface VichanApi {
    /** Site-wide board list. Not all engines expose this; callers handle a 404 gracefully. */
    suspend fun boards(): VichanBoardsResponse

    /** Full catalog (all pages) for a board. */
    suspend fun catalog(board: String): List<VichanCatalogPage>

    /** OP numbers retained by 4chan's read-only archive for boards that enable it. */
    suspend fun archive(board: String): List<Long>

    /** A single thread by its OP number. */
    suspend fun thread(
        board: String,
        no: Long,
    ): VichanThreadResponse
}

/**
 * [VichanApi] over Ktor, so the same code runs on Android (OkHttp engine) and iOS (Darwin engine).
 *
 * Non-2xx responses surface as Ktor's `ResponseException` and transport failures as `IOException`;
 * [com.orbin.provider.vichan.VichanProvider] maps both to the provider contract.
 */
class KtorVichanApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val json: Json,
) : VichanApi {
    override suspend fun boards(): VichanBoardsResponse = get(VichanBoardsResponse.serializer(), "boards.json")

    override suspend fun catalog(board: String): List<VichanCatalogPage> {
        requireSafeSegment(board)
        return get(ListSerializer(VichanCatalogPage.serializer()), board, "catalog.json")
    }

    override suspend fun archive(board: String): List<Long> {
        requireSafeSegment(board)
        return get(ListSerializer(Long.serializer()), board, "archive.json")
    }

    override suspend fun thread(
        board: String,
        no: Long,
    ): VichanThreadResponse {
        requireSafeSegment(board)
        return get(VichanThreadResponse.serializer(), board, "thread", "$no.json")
    }

    private suspend fun <T> get(
        deserializer: DeserializationStrategy<T>,
        vararg path: String,
    ): T {
        val response =
            client.get {
                expectSuccess = true
                url {
                    takeFrom(baseUrl)
                    appendPathSegments(*path)
                }
            }
        val channel = response.bodyAsChannel()
        val bytes = channel.readBuffer(MAX_JSON_BYTES.toLong() + 1).readByteArray()
        if (bytes.size > MAX_JSON_BYTES) {
            channel.cancel(null)
            throw ProviderException.Parse("Response exceeded maximum allowed size")
        }
        return json.decodeFromString(deserializer, bytes.decodeToString())
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
