package com.orbin.domain.repository

import com.orbin.core.model.SavedThreadSummary
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadKey
import kotlinx.coroutines.flow.Flow

/**
 * Threads the reader chose to keep.
 *
 * Threads are pruned upstream — that is the defining property of the platform — so one read an
 * hour ago can be gone. Downloads already covered a thread's media; this covers its text, and a
 * saved copy is served when the live thread can no longer be fetched.
 */
interface SavedThreadRepository {
    /** Saved threads, most recently saved first. */
    fun observeSaved(): Flow<List<SavedThreadSummary>>

    /** True while [key] has a saved copy, so the UI can offer to save or forget it. */
    fun isSaved(key: ThreadKey): Flow<Boolean>

    /** Stores [thread], replacing any earlier copy so re-saving captures newer replies. */
    suspend fun save(thread: Thread)

    /** The saved copy of [key], or null when there isn't one. */
    suspend fun load(key: ThreadKey): Thread?

    suspend fun forget(key: ThreadKey)
}
