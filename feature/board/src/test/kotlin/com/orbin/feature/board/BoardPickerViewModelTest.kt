package com.orbin.feature.board

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBoardRepository
import com.orbin.core.testing.repository.FakeImageBoardProvider
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class BoardPickerViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun hiddenNsfwSettingUpdatesChoicesWithoutConflatingSitesWithTheSameBoardPath() =
        runTest {
            val settings = FakeSettingsRepository(AppSettings.Default.copy(hideNsfwBoards = false))
            val viewModel =
                BoardPickerViewModel(
                    FakeBoardRepository(
                        listOf(Board(BoardId("g"), "General"), Board(BoardId("other"), "Other", isNsfw = true)),
                    ),
                    registry(),
                    settings,
                )
            viewModel.options.filter { it.isNotEmpty() }.test {
                val visible = awaitItem()
                assertThat(visible).hasSize(4)
                assertThat(visible.filter { it.boardId == "g" }.map { it.providerId })
                    .containsExactly("fourchan", "bbwchan")

                settings.setHideNsfwBoards(true)

                val filtered = awaitItem()
                assertThat(filtered).hasSize(2)
                assertThat(filtered.map { it.boardId }).containsExactly("g", "g")
            }
        }

    @Test
    fun browserActionUsesTheOwningSiteAndIsAbsentForAnUnknownProvider() {
        val viewModel = BoardPickerViewModel(FakeBoardRepository(), registry(), FakeSettingsRepository())
        assertThat(viewModel.boardWebUrl("fourchan", "g")).isEqualTo("https://boards.4chan.org/g/")
        assertThat(viewModel.boardWebUrl("bbwchan", "g")).isEqualTo("https://example.org/g/")
        assertThat(viewModel.boardWebUrl("missing", "g")).isNull()
    }

    private fun registry(): ProviderRegistry =
        object : ProviderRegistry {
            private val providers =
                listOf(FakeImageBoardProvider("fourchan", "4chan"), FakeImageBoardProvider("bbwchan", "BBWChan"))

            override fun all(): List<ImageBoardProvider> = providers

            override fun get(id: ProviderId): ImageBoardProvider? = providers.firstOrNull { it.metadata.id == id }

            override fun default(): ImageBoardProvider = providers.first()
        }
}
