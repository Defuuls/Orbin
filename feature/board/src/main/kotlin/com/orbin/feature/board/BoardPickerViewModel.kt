package com.orbin.feature.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.Board
import com.orbin.core.model.ProviderId
import com.orbin.core.model.matchesFilterTokens
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.api.ProviderRegistry
import com.orbin.uinext.ReaderBoardOption
import com.orbin.uinext.sourceLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Cached board choices retain provider identity when board paths are shared by several sites. */
@HiltViewModel
class BoardPickerViewModel
    @Inject
    constructor(
        private val boards: BoardRepository,
        providers: ProviderRegistry,
        settings: SettingsRepository,
    ) : ViewModel() {
        private val sites = providers.all()
        private val cachedBoards =
            combine(
                sites.map { site ->
                    boards.observeBoards(site.metadata.id).map { entries ->
                        entries.map { SiteBoard(site.metadata, it) }
                    }
                },
            ) { entries -> entries.flatMap { it } }

        val options =
            cachedBoards
                .combine(settings.settings) { entries, current ->
                    entries
                        .filterNot { current.hideNsfwBoards && it.board.isNsfw }
                        .filterNot { it.board.matchesFilterTokens(emptySet()) }
                        .map { entry ->
                            ReaderBoardOption(
                                entry.provider.id.value,
                                sourceLabel(entry.provider.displayName),
                                entry.board.id.value,
                                entry.board.title,
                            )
                        }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun refresh() {
            sites.forEach { site -> viewModelScope.launch { boards.refreshBoards(site.metadata.id) } }
        }

        fun boardWebUrl(
            providerId: String,
            boardId: String,
        ): String? {
            val site = sites.firstOrNull { it.metadata.id == ProviderId(providerId) } ?: return null
            val root = if (providerId == "fourchan") "https://boards.4chan.org" else site.metadata.baseUrl.trimEnd('/')
            return "$root/$boardId/"
        }
    }

private data class SiteBoard(
    val provider: ProviderMetadata,
    val board: Board,
)
