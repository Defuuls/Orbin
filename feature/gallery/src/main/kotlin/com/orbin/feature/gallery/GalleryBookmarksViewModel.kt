package com.orbin.feature.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.Bookmark
import com.orbin.core.model.ThreadKey
import com.orbin.domain.repository.BookmarkRepository
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

/** Exposes the user's bookmarks and watch/remove actions for the gallery's Bookmarks tab. */
@HiltViewModel
class GalleryBookmarksViewModel
    @Inject
    constructor(
        private val repository: BookmarkRepository,
    ) : ViewModel() {
        val bookmarks: StateFlow<ImmutableList<Bookmark>> =
            repository
                .observeBookmarks()
                .map { it.toImmutableList() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), persistentListOf())

        fun remove(key: ThreadKey) = viewModelScope.launch { repository.removeBookmark(key) }

        fun toggleWatched(
            key: ThreadKey,
            watched: Boolean,
        ) = viewModelScope.launch { repository.setWatched(key, watched) }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
