package com.orbin.provider.vichan

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogSort
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.provider.api.EngineKind
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.vichan.api.VichanApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * [ImageBoardProvider] for vichan/4chan-compatible engines. A single instance targets one
 * [VichanSite]; multiple sites = multiple registered providers. The provider owns its Retrofit
 * service (built from the shared [okhttp3.OkHttpClient]) and maps all transport failures to the
 * [ProviderException] contract so callers never see Retrofit/OkHttp types.
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
            } catch (e: HttpException) {
                throw when (e.code()) {
                    HTTP_NOT_FOUND -> ProviderException.NotFound("Resource not found", e)
                    HTTP_TOO_MANY_REQUESTS -> ProviderException.RateLimited(e.retryAfterSeconds())
                    else -> ProviderException.Http(e.code(), e.message(), e)
                }
            } catch (e: IOException) {
                throw ProviderException.Network("Network error: ${e.message}", e)
            } catch (e: SerializationException) {
                throw ProviderException.Parse("Failed to parse response", e)
            }
        }

    private fun HttpException.retryAfterSeconds(): Long? {
        val retryAfter = response()?.headers()?.get("Retry-After") ?: return null
        return retryAfter.toLongOrNull() ?: retryAfter.httpDateDelaySeconds()
    }

    private fun String.httpDateDelaySeconds(): Long? =
        runCatching {
            val retryAt = ZonedDateTime.parse(this, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            Duration.between(Instant.now(), retryAt).seconds.coerceAtLeast(0)
        }.getOrNull()

    private fun CatalogSort.comparator(): Comparator<CatalogThread> =
        when (this) {
            CatalogSort.CREATION_DATE ->
                compareByDescending { it.originalPost.createdAtMillis }
            CatalogSort.REPLY_COUNT ->
                compareByDescending { it.stats.replyCount }
            CatalogSort.IMAGE_COUNT ->
                compareByDescending { it.stats.imageCount }
            CatalogSort.BUMP_ORDER, CatalogSort.LAST_REPLY ->
                compareByDescending { it.stats.lastModifiedMillis }
        }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
