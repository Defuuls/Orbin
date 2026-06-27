package com.orbin.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogSort
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.ProviderId
import com.orbin.domain.repository.CatalogRepository
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderRegistry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val PAGE_SIZE = 20

/**
 * Catalog repository exposing a Paging 3 stream. The vichan/4chan catalog endpoint returns the
 * whole catalog in one response, so [CatalogPagingSource] fetches it once and pages over the
 * in-memory list — giving the UI incremental rendering and prefetch without extra requests.
 */
@Singleton
class CatalogRepositoryImpl
    @Inject
    constructor(
        private val registry: ProviderRegistry,
    ) : CatalogRepository {
        override fun catalogStream(
            provider: ProviderId,
            board: BoardId,
            sort: CatalogSort,
        ): Flow<PagingData<CatalogThread>> =
            Pager(
                config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
                pagingSourceFactory = {
                    CatalogPagingSource(registry, CatalogRequest(provider, board, sort = sort))
                },
            ).flow
    }

/** Pages over a single catalog response held in memory for the lifetime of the source. */
private class CatalogPagingSource(
    private val registry: ProviderRegistry,
    private val request: CatalogRequest,
) : PagingSource<Int, CatalogThread>() {
    private var cached: List<CatalogThread>? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CatalogThread> =
        try {
            val provider =
                registry.get(request.provider)
                    ?: return LoadResult.Error(IllegalStateException("Unknown provider"))
            val all = cached ?: provider.getCatalog(request).also { cached = it }

            val offset = params.key ?: 0
            val end = minOf(offset + params.loadSize, all.size)
            val slice = if (offset >= all.size) emptyList() else all.subList(offset, end)

            LoadResult.Page(
                data = slice,
                prevKey = if (offset == 0) null else (offset - PAGE_SIZE).coerceAtLeast(0),
                nextKey = if (end >= all.size) null else end,
            )
        } catch (e: ProviderException) {
            LoadResult.Error(e)
        }

    override fun getRefreshKey(state: PagingState<Int, CatalogThread>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(PAGE_SIZE)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(PAGE_SIZE)
        }
}
