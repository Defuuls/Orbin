package com.orbin.provider.api

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.SearchQuery
import com.orbin.core.model.SearchResult
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import java.util.ArrayDeque

/** Privacy-safe timing/outcome record. It intentionally contains no board, thread, query or URL. */
data class ProviderDiagnosticEvent(
    val provider: String,
    val operation: String,
    val durationMillis: Long,
    val outcome: String,
)

interface ProviderDiagnostics {
    fun record(event: ProviderDiagnosticEvent)
    fun snapshot(): List<ProviderDiagnosticEvent>
    fun clear()
}

/** Small bounded in-memory ring buffer suitable for debug/recovery reports without telemetry. */
class InMemoryProviderDiagnostics(
    private val capacity: Int = 100,
) : ProviderDiagnostics {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val events = ArrayDeque<ProviderDiagnosticEvent>(capacity)

    @Synchronized
    override fun record(event: ProviderDiagnosticEvent) {
        if (events.size == capacity) events.removeFirst()
        events.addLast(event)
    }

    @Synchronized
    override fun snapshot(): List<ProviderDiagnosticEvent> = events.toList()

    @Synchronized
    override fun clear() = events.clear()
}

/**
 * Decorates any provider with contract validation plus privacy-safe timing diagnostics. This keeps
 * observability and SPI enforcement in one place rather than duplicating it across engines.
 */
class InstrumentedImageBoardProvider(
    private val delegate: ImageBoardProvider,
    private val diagnostics: ProviderDiagnostics,
) : ImageBoardProvider {
    override val metadata: ProviderMetadata get() = delegate.metadata
    override val capabilities: ProviderCapabilities get() = delegate.capabilities

    override suspend fun getBoards(): List<Board> =
        measured("boards") { delegate.getBoards().also(ProviderContract::requireValidBoards) }

    override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> =
        measured("catalog") { delegate.getCatalog(request).also(ProviderContract::requireValidCatalog) }

    override suspend fun getThread(board: BoardId, thread: ThreadId): Thread =
        measured("thread") { delegate.getThread(board, thread).also(ProviderContract::requireValidThread) }

    override suspend fun search(query: SearchQuery): List<SearchResult> =
        measured("search") { delegate.search(query) }

    private suspend fun <T> measured(operation: String, block: suspend () -> T): T {
        val started = System.nanoTime()
        return try {
            block().also { record(operation, started, "success") }
        } catch (throwable: Throwable) {
            record(operation, started, throwable.javaClass.simpleName.ifBlank { "failure" })
            throw throwable
        }
    }

    private fun record(operation: String, startedNanos: Long, outcome: String) {
        diagnostics.record(
            ProviderDiagnosticEvent(
                provider = metadata.id.value,
                operation = operation,
                durationMillis = (System.nanoTime() - startedNanos) / 1_000_000,
                outcome = outcome,
            ),
        )
    }
}
