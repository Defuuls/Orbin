package com.orbin.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.DownloadRecord
import com.orbin.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Exposes download history and refresh/clear actions. */
@HiltViewModel
class DownloadsViewModel
    @Inject
    constructor(
        private val repository: DownloadRepository,
    ) : ViewModel() {
        val downloads: StateFlow<ImmutableList<DownloadRecord>> =
            repository
                .observeDownloads()
                .map { it.toImmutableList() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), persistentListOf())

        init {
            refresh()
        }

        fun refresh() = viewModelScope.launch { repository.refreshStatuses() }

        fun clear() = viewModelScope.launch { repository.clearHistory() }

        fun retry(id: Long) = viewModelScope.launch { repository.retry(id) }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
