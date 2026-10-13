package com.orbin.domain.repository

import com.orbin.core.model.Thread

/** A thread saved as a page: [fileName] at [path], with [savedMedia] of [totalMedia] files inside. */
data class ThreadArchive(
    val path: String,
    val fileName: String,
    val savedMedia: Int,
    val totalMedia: Int,
)

/**
 * Saves a whole thread for keeps: a .zip holding an `index.html` that opens in any browser, its
 * `style.css`, and every file and thumbnail in the thread, linked from the page by relative path.
 */
interface ThreadArchiveRepository {
    /**
     * Writes [thread]'s archive into [directory] (a temporary folder when null) and returns it.
     * [onProgress] counts media files fetched. A file that can't be fetched stays a link to the site.
     */
    suspend fun archive(
        thread: Thread,
        directory: String? = null,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ThreadArchive
}

/** No archiving, for screens and tests built without one; it always fails. */
object DisabledThreadArchive : ThreadArchiveRepository {
    override suspend fun archive(
        thread: Thread,
        directory: String?,
        onProgress: (done: Int, total: Int) -> Unit,
    ): ThreadArchive = error("Saving threads isn't available here")
}
