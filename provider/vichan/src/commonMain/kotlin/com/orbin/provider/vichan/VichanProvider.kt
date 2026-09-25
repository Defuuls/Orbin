package com.orbin.provider.vichan

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogSort
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.comparator
import com.orbin.provider.api.EngineKind
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.vichan.api.VichanApi
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpHeaders
import io.ktor.http.fromHttpToGmtDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.time.Clock

/**
 * [ImageBoardProvider] for vichan/4chan-compatible engines. A single instance targets one
 * [VichanSite]; multiple sites = multiple registered providers. The provider talks to the engine
 * through [VichanApi] and maps all transport failures to the [ProviderException] contract, so
 * callers never see Ktor types.
 */
class VichanProvider(
    private val site: VichanSite,
    private val api: VichanApi,
    private val ioDispatcher: CoroutineDispatcher,
) : ImageBoardProvider {
    private val mapper = VichanMapper(site)

    override val metadata: ProviderMetadata =
        ProviderMetadata(
            id = site.providerId,
            displayName = site.displayName,
            baseUrl = site.siteUrl,
            engine = EngineKind.VICHAN,
            isNsfwByDefault = site.nsfwByDefault,
        )

    override val capabilities: ProviderCapabilities =
        ProviderCapabilities(
            supportsBoardList = site.supportsBoardList,
            supportsCatalog = true,
            supportsThreads = true,
            supportsSearch = false,
            supportsArchive = site.supportsArchive,
            // The catalog endpoint returns every page at once, so app-side paging slices that list.
            supportsCatalogPaging = true,
            supportedSortOptions =
                setOf(
                    CatalogSort.BUMP_ORDER,
                    CatalogSort.CREATION_DATE,
                    CatalogSort.REPLY_COUNT,
                    CatalogSort.IMAGE_COUNT,
                    CatalogSort.SUBJECT,
                ),
        )

    override suspend fun getBoards(): List<Board> =
        call {
            if (!site.supportsBoardList) throw ProviderException.Unsupported("boards")
            mapper.mapBoards(api.boards())
        }

    override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> =
        call {
            val threads = mapper.mapCatalog(request.board, api.catalog(request.board.value))
            threads.sortedWith(request.sort.comparator())
        }

    override suspend fun getThread(
        board: BoardId,
        thread: ThreadId,
    ): Thread =
        call {
            mapper.mapThread(board, api.thread(board.value, thread.value))
        }

    /** Runs [block] on the IO dispatcher and normalizes failures to [ProviderException]. */
    private suspend fun <T> call(block: suspend () -> T): T =
        withContext(ioDispatcher) {
            try {
                block()
            } catch (e: ProviderException) {
                throw e
            } catch (e: ResponseException) {
                val status = e.response.status
                throw when (status.value) {
                    HTTP_NOT_FOUND -> ProviderException.NotFound("Resource not found", e)
                    HTTP_TOO_MANY_REQUESTS -> ProviderException.RateLimited(e.retryAfterSeconds())
                    else -> ProviderException.Http(status.value, status.description, e)
                }
            } catch (e: IOException) {
                throw ProviderException.Network("Network error: ${e.message}", e)
            } catch (e: SerializationException) {
                throw ProviderException.Parse("Failed to parse response", e)
            }
        }

    private fun ResponseException.retryAfterSeconds(): Long? {
        val retryAfter = response.headers[HttpHeaders.RetryAfter] ?: return null
        return retryAfter.toLongOrNull() ?: retryAfter.httpDateDelaySeconds()
    }

    private fun String.httpDateDelaySeconds(): Long? =
        runCatching {
            val retryAtMillis = fromHttpToGmtDate().timestamp
            ((retryAtMillis - Clock.System.now().toEpochMilliseconds()) / MILLIS_PER_SECOND).coerceAtLeast(0)
        }.getOrNull()

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val MILLIS_PER_SECOND = 1_000L
    }
}
