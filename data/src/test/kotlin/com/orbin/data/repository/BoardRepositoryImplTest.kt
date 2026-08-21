package com.orbin.data.repository

import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.data.database.dao.BoardDao
import com.orbin.data.database.entity.BoardEntity
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val PROVIDER = "fourchan"
private val providerId = ProviderId(PROVIDER)
private const val DAY_MILLIS = 24L * 60 * 60 * 1000

@OptIn(ExperimentalCoroutinesApi::class)
class BoardRepositoryImplTest {
    private val dao = mockk<BoardDao>(relaxUnitFun = true)
    private val provider = mockk<ImageBoardProvider>()
    private val registry =
        mockk<ProviderRegistry> {
            every { get(providerId) } returns provider
        }
    private val repository = BoardRepositoryImpl(registry, dao, UnconfinedTestDispatcher())

    private fun board(
        id: String,
        title: String = id.uppercase(),
    ) = Board(id = BoardId(id), title = title)

    private fun entity(
        id: String,
        sortIndex: Int,
        cachedAtMillis: Long = System.currentTimeMillis(),
    ) = BoardEntity(
        provider = PROVIDER,
        id = id,
        title = id.uppercase(),
        description = "",
        category = "",
        isNsfw = false,
        pageCount = null,
        bumpLimit = null,
        imageLimit = null,
        maxCommentChars = null,
        supportsMedia = true,
        sortIndex = sortIndex,
        cachedAtMillis = cachedAtMillis,
    )

    @Test
    fun `serves the cached list without refreshing when it is fresh`() =
        runTest {
            coEvery { dao.cachedAtMillis(PROVIDER) } returns System.currentTimeMillis()
            every { dao.observeBoards(PROVIDER) } returns flowOf(listOf(entity("g", 0), entity("a", 1)))

            val boards = repository.observeBoards(providerId).first()

            assertThat(boards.map { it.id.value }).containsExactly("g", "a").inOrder()
            coVerify(exactly = 0) { provider.getBoards() }
        }

    @Test
    fun `refreshes when nothing is cached`() =
        runTest {
            coEvery { dao.cachedAtMillis(PROVIDER) } returns null
            coEvery { provider.getBoards() } returns listOf(board("g"))
            every { dao.observeBoards(PROVIDER) } returns flowOf(emptyList())

            repository.observeBoards(providerId).first()

            coVerify(exactly = 1) { provider.getBoards() }
        }

    @Test
    fun `refreshes when the cache is older than a day`() =
        runTest {
            coEvery { dao.cachedAtMillis(PROVIDER) } returns System.currentTimeMillis() - DAY_MILLIS - 1
            coEvery { provider.getBoards() } returns listOf(board("g"))
            every { dao.observeBoards(PROVIDER) } returns flowOf(listOf(entity("g", 0)))

            repository.observeBoards(providerId).first()

            coVerify(exactly = 1) { provider.getBoards() }
        }

    @Test
    fun `refresh writes the provider's own ordering through to the cache`() =
        runTest {
            coEvery { provider.getBoards() } returns listOf(board("g"), board("a"), board("v"))
            val written = slot<List<BoardEntity>>()
            coEvery { dao.replaceBoards(PROVIDER, capture(written)) } returns Unit

            repository.refreshBoards(providerId)

            // Board lists are curated rather than alphabetical, so position is data, not a detail.
            assertThat(written.captured.map { it.id to it.sortIndex })
                .containsExactly("g" to 0, "a" to 1, "v" to 2)
                .inOrder()
        }

    /**
     * observeBoards filtered these out already; refreshBoards returned the provider's list as-is,
     * so a caller taking the refresh result directly — the board gallery does — saw boards the
     * always-on filter exists to remove.
     */
    @Test
    fun `refresh does not return boards the permanent filter catches`() =
        runTest {
            coEvery { provider.getBoards() } returns listOf(board("g"), board("gore"), board("v"))

            val result = repository.refreshBoards(providerId)

            assertThat(result.isSuccess).isTrue()
            assertThat((result as OrbinResult.Success).data.map { it.id.value })
                .containsExactly("g", "v")
                .inOrder()
        }

    @Test
    fun `a failed refresh leaves the cached boards alone`() =
        runTest {
            coEvery { provider.getBoards() } throws ProviderException.Network("host unreachable")

            val result = repository.refreshBoards(providerId)

            assertThat(result.isSuccess).isFalse()
            // Stale boards beat no boards: the cache must survive a failed refresh.
            coVerify(exactly = 0) { dao.replaceBoards(any(), any()) }
        }
}
