package com.orbin.domain.repository

import kotlinx.coroutines.flow.Flow

/** Which release's "What's new" the reader has already seen, so each release shows it once. */
interface WhatsNewRepository {
    /** The version whose notes were last shown, or null before the first. */
    val lastSeen: Flow<String?>

    suspend fun markSeen(version: String)
}
