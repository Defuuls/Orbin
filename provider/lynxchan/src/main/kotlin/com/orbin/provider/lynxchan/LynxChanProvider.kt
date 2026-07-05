package com.orbin.provider.lynxchan

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
import com.orbin.provider.lynxchan.api.LynxChanApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/**
 * [ImageBoardProvider] for LynxChan engines. A single instance targets one [LynxChanSite];
 * multiple sites = multiple registered providers. The provider owns its Retrofit service (built
 * from the shared [okhttp3.OkHttpClient]) and maps all transport failures to the
 * [ProviderException] contract so callers never see Retrofit/OkHttp types.
 */
class LynxChanProvider(
    private val site: LynxChanSite,
    private val api: LynxChanApi,
    private val ioDispatcher: CoroutineDispatcher,
) : ImageBoardProvider {
    private val mapper = LynxChanMapper(site)

    override val metadata: ProviderMetadata =
        ProviderMetadata(
            id = site.providerId,
            displayName = site.displayName,
            baseUrl = site.siteUrl,
            engine = EngineKind.LYNXCHAN,
            isNsfwByDefault = site.nsfwByDefault,
        )

    override val capabilities: ProviderCapabilities =
        ProviderCapabilities(
            supportsBoardList = true,
            supportsCatalog = true,
            supportsThreads = true,
            supportsSearch = false,
            // catalog.json returns every active thread on the board at once; app-side paging slices it.
            supportsCatalogPaging = true,
            supportedSortOptions =
                setOf(
                    CatalogSort.BUMP_ORDER,
                    CatalogSort.REPLY_COUNT,
                    CatalogSort.IMAGE_COUNT,
                    CatalogSort.LAST_REPLY,
                ),
        )

    override suspend fun getBoards(): List<Board> = call { mapper.mapBoards(api.boards()) }

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

    private fun HttpException.retryAfterSeconds(): Long? = response()?.headers()?.get("Retry-After")?.toLongOrNull()

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
