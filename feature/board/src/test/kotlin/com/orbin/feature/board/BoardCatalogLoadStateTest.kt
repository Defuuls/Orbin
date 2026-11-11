package com.orbin.feature.board

import androidx.paging.LoadState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BoardCatalogLoadStateTest {
    @Test
    fun `first load shows loading until a snapshot is available`() {
        val state = boardCatalogLoadState(itemCount = 0, refresh = LoadState.Loading)

        assertThat(state.content).isEqualTo(BoardCatalogContent.LOADING)
        assertThat(state.isRefreshing).isTrue()
        assertThat(state.failure).isNull()
    }

    @Test
    fun `refresh keeps the loaded catalog visible`() {
        val state = boardCatalogLoadState(itemCount = 3, refresh = LoadState.Loading)

        assertThat(state.content).isEqualTo(BoardCatalogContent.CONTENT)
        assertThat(state.isRefreshing).isTrue()
    }

    @Test
    fun `failed refresh keeps content and exposes the original cause for retry feedback`() {
        val failure = IllegalStateException("HTTP 503")
        val state = boardCatalogLoadState(itemCount = 3, refresh = LoadState.Error(failure))

        assertThat(state.content).isEqualTo(BoardCatalogContent.CONTENT)
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.failure).isSameInstanceAs(failure)
    }

    @Test
    fun `initial failure occupies the empty catalog pane`() {
        val failure = IllegalStateException("HTTP 404")
        val state = boardCatalogLoadState(itemCount = 0, refresh = LoadState.Error(failure))

        assertThat(state.content).isEqualTo(BoardCatalogContent.ERROR)
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.failure).isSameInstanceAs(failure)
    }

    @Test
    fun `completed empty snapshot replaces an older failure`() {
        val state = boardCatalogLoadState(itemCount = 0, refresh = LoadState.NotLoading(endOfPaginationReached = true))

        assertThat(state.content).isEqualTo(BoardCatalogContent.EMPTY)
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.failure).isNull()
    }

    @Test
    fun `successful reload leaves content and clears refresh feedback`() {
        val state = boardCatalogLoadState(itemCount = 3, refresh = LoadState.NotLoading(endOfPaginationReached = true))

        assertThat(state.content).isEqualTo(BoardCatalogContent.CONTENT)
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.failure).isNull()
    }
}
