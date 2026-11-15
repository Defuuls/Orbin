package com.orbin.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.watchedNewReplies
import com.orbin.domain.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Library tab's new-replies count over watched threads. */
@HiltViewModel
class WatchedBadgeViewModel
    @Inject
    constructor(
        repository: BookmarkRepository,
    ) : ViewModel() {
        val newReplies =
            repository
                .observeBookmarks()
                .map { it.watchedNewReplies() }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
