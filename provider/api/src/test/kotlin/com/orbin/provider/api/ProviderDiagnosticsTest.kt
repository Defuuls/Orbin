package com.orbin.provider.api

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProviderDiagnosticsTest {
    @Test
    fun `ring buffer evicts oldest event`() {
        val diagnostics = InMemoryProviderDiagnostics(capacity = 2)
        diagnostics.record(ProviderDiagnosticEvent("a", "boards", 1, "success"))
        diagnostics.record(ProviderDiagnosticEvent("a", "catalog", 2, "success"))
        diagnostics.record(ProviderDiagnosticEvent("a", "thread", 3, "success"))

        assertThat(diagnostics.snapshot().map { it.operation }).containsExactly("catalog", "thread").inOrder()
    }

    @Test
    fun `instrumented provider records successful operation`() = runTest {
        val diagnostics = InMemoryProviderDiagnostics()
        val provider = InstrumentedImageBoardProvider(FakeProvider(), diagnostics)

        provider.getBoards()

        val event = diagnostics.snapshot().single()
        assertThat(event.provider).isEqualTo("test")
        assertThat(event.operation).isEqualTo("boards")
        assertThat(event.outcome).isEqualTo("success")
        assertThat(event.durationMillis).isAtLeast(0L)
    }

    private class FakeProvider : ImageBoardProvider {
        override val metadata = ProviderMetadata(ProviderId("test"), "Test", "https://example.test")
        override val capabilities = ProviderCapabilities()

        override suspend fun getBoards(): List<Board> = listOf(Board(BoardId("a"), "A"))

        override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> = emptyList()

        override suspend fun getThread(board: BoardId, thread: ThreadId): Thread = error("not needed")
    }
}
