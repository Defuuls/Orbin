package com.orbin.ios

import com.orbin.core.model.Thread
import com.orbin.domain.repository.ThreadArchive
import com.orbin.domain.repository.ThreadArchiveRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Saving a whole thread as a page on iOS: the shared archive (index.html, style.css and every file)
 * is written to Orbin's folder in the Files app, then offered to the share sheet ([onSaved]) so it
 * can go straight to AirDrop, another folder or another app.
 */
class ThreadPages(
    private val archive: ThreadArchiveRepository,
    private val folder: String,
    private val onSaved: (ThreadArchive) -> Unit = { shareFile(it.path) },
) {
    private val _saving = MutableStateFlow(false)

    /** True while a page is being saved, so a second tap does nothing. */
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** Saves [thread]; the saved archive, or null if one was already saving or this one failed. */
    suspend fun save(thread: Thread): ThreadArchive? {
        if (!_saving.compareAndSet(expect = false, update = true)) return null
        return try {
            archive.archive(thread, folder).also(onSaved)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            // A full disk or a folder iOS took away: nothing is saved and the tap can be tried again.
            @Suppress("TooGenericExceptionCaught") _: Exception,
        ) {
            null
        } finally {
            _saving.value = false
        }
    }
}
