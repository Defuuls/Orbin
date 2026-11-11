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
import com.orbin.domain.filter.ContentPolicy
import com.orbin.domain.repository.CatalogRepository
import com.orbin.provider.api.ProviderRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

private const val PAGE_SIZE = 100
private const val INITIAL_LOAD_SIZE = 500

class CatalogRepositoryImpl(
    private val registry: ProviderRegistry,
) : CatalogRepository {
    override fun catalogStream(
        provider: ProviderId,
        board: BoardId,
        sort: CatalogSort,
    ): Flow<PagingData<CatalogThread>> = catalogStreamWhileVisible(provider, board, sort, MutableStateFlow(true))

    override fun catalogStreamWhileVisible(
        provider: ProviderId,
        board: BoardId,
        sort: CatalogSort,
        visible: StateFlow<Boolean>,
        onRefreshFailure: ((Throwable?) -> Unit)?,
    ): Flow<PagingData<CatalogThread>> {
        val session = CatalogSession(registry, CatalogRequest(provider, board, sort = sort), visible, onRefreshFailure)
        return Pager(
            config =
                PagingConfig(
                    pageSize = PAGE_SIZE,
                    initialLoadSize = INITIAL_LOAD_SIZE,
                    enablePlaceholders = false,
                ),
            pagingSourceFactory = { CatalogPagingSource(session) },
        ).flow
    }
}

/** Paging generations share the last successful revision, including a canceled in-place refresh. */
private class CatalogSession(
    private val registry: ProviderRegistry,
    private val request: CatalogRequest,
    private val visible: StateFlow<Boolean>,
    private val onRefreshFailure: ((Throwable?) -> Unit)?,
) {
    private var loaded: List<CatalogThread>? = null

    @Suppress("TooGenericExceptionCaught")
    suspend fun load(): List<CatalogThread> {
        onRefreshFailure?.invoke(null)
        while (true) {
            visible.first { it }
            val result =
                supervisorScope {
                    val fetch = async { fetchVisible() }
                    var hiddenByVisibility = false
                    val hidden =
                        launch {
                            visible.first { !it }
                            hiddenByVisibility = true
                            fetch.cancel(CatalogHidden())
                        }
                    try {
                        fetch.await()
                    } catch (_: CatalogHidden) {
                        currentCoroutineContext().ensureActive()
                        loaded
                    } catch (cancelled: CancellationException) {
                        currentCoroutineContext().ensureActive()
                        if (!hiddenByVisibility) throw cancelled
                        loaded
                    } catch (error: Exception) {
                        currentCoroutineContext().ensureActive()
                        val retained = loaded
                        if (retained == null || onRefreshFailure == null) throw error
                        // Keep a populated cached generation for collectors returning from a route.
                        // The host still receives the cause and an explicit refresh retry action.
                        onRefreshFailure(error)
                        retained
                    } finally {
                        hidden.cancel()
                    }
                }
            if (result != null) {
                loaded = result
                return result
            }
            // An interrupted first load has nothing to retain. Resume only when its pane returns.
        }
    }

    private suspend fun fetchVisible(): List<CatalogThread> {
        if (!visible.value) throw CatalogHidden()
        return withContext(Dispatchers.Default) {
            val provider = registry.get(request.provider) ?: error("Unknown provider")
            ContentPolicy.visibleCatalog(provider.getCatalog(request))
        }
    }
}

private class CatalogHidden : CancellationException("Catalog is hidden")

private class CatalogPagingSource(
    private val session: CatalogSession,
) : PagingSource<Int, CatalogThread>() {
    private var cached: List<CatalogThread>? = null

    @Suppress("TooGenericExceptionCaught")
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CatalogThread> =
        try {
            val all = cached ?: session.load().also { cached = it }

            val offset = params.key ?: 0
            val end = minOf(offset + params.loadSize, all.size)
            val slice = if (offset >= all.size) emptyList() else all.subList(offset, end)

            LoadResult.Page(
                data = slice,
                prevKey = if (offset == 0) null else (offset - PAGE_SIZE).coerceAtLeast(0),
                nextKey = if (end >= all.size) null else end,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }

    override fun getRefreshKey(state: PagingState<Int, CatalogThread>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.let { page ->
                page.prevKey?.plus(PAGE_SIZE) ?: page.nextKey?.minus(page.data.size)
            }
        }
}
