package com.orbin.ios

import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.MediaLibraryRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.repository.ThreadMemoryRepository

/** The shared durable stores observed and merged by portable cloud synchronization. */
data class CloudRepositories(
    val settings: SettingsRepository,
    val boardPreferences: BoardPreferencesRepository,
    val bookmarks: BookmarkRepository,
    val history: HistoryRepository,
    val memory: ThreadMemoryRepository,
    val media: MediaLibraryRepository,
)
