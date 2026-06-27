package com.orbin.provider.api

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.SearchQuery
import com.orbin.core.model.SearchResult
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId

/**
 * The provider Service Provider Interface (SPI): the single seam through which the app talks to
 * any image board engine. Add support for a new engine (vichan, LynxChan, TinyIB, …) by
 * implementing this interface and registering the implementation — nothing in the domain, data,
 * or UI layers needs to change.
 *
 * Contract:
 * - All methods are `suspend` and must be safe to call from any dispatcher; implementations move
 *   blocking I/O onto an appropriate dispatcher themselves.
 * - Failures are reported by throwing a [ProviderException] subtype. Implementations must not
 *   leak transport-specific exceptions (IOException, HttpException, …) to callers.
 * - Methods for capabilities reported as unsupported in [capabilities] should throw
 *   [ProviderException.Unsupported].
 * - Returned models are fully resolved (absolute URLs, parsed comments); the data layer adds only
 *   cross-cutting enrichment such as backlinks.
 */
interface ImageBoardProvider {

    val metadata: ProviderMetadata

    val capabilities: ProviderCapabilities

    /** Lists the boards offered by this provider. */
    suspend fun getBoards(): List<Board>

    /** Loads a single catalog page for [request]. */
    suspend fun getCatalog(request: CatalogRequest): List<CatalogThread>

    /** Loads a full thread (OP + all replies). */
    suspend fun getThread(board: BoardId, thread: ThreadId): Thread

    /**
     * Server-side search. Only called when [capabilities] reports `supportsSearch`; the default
     * implementation rejects the call so providers without search need not override it.
     */
    suspend fun search(query: SearchQuery): List<SearchResult> =
        throw ProviderException.Unsupported("search")
}
