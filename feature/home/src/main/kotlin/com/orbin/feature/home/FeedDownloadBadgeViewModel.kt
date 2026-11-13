package com.orbin.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Primary destinations observe queue membership without recomposing for byte progress. */
@HiltViewModel
class FeedDownloadBadgeViewModel
    @Inject
    constructor(
        repository: DownloadRepository,
    ) : ViewModel() {
        val activeCount =
            repository
                .observeDownloads()
                .map { records -> records.count { it.status.isActive } }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
