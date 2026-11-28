package com.orbin.app.pulse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.Bookmark
import com.orbin.domain.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The watched threads that have new replies, which is what "needs you" means in the stream. */
@HiltViewModel
class PulseWatchedViewModel
    @Inject
    constructor(
        repository: BookmarkRepository,
    ) : ViewModel() {
        val needingYou: StateFlow<List<Bookmark>> =
            repository
                .observeBookmarks()
                .map { bookmarks -> bookmarks.filter { it.isWatched && it.hasUnread } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
