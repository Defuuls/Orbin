package com.orbin.core.reader

import androidx.compose.runtime.Composable
import com.orbin.core.reader.resources.Res
import com.orbin.core.reader.resources.reader_status_counts
import com.orbin.core.reader.resources.reader_status_files
import com.orbin.core.reader.resources.reader_status_replies
import com.orbin.core.reader.resources.reader_status_saved
import com.orbin.core.reader.resources.reader_status_updated
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Only resource interpolation happens here; dates and rich bodies were formatted with the load. */
@Composable
fun readerThreadStatus(presentation: ThreadPresentationIndex): String {
    val replies = presentation.replyCount
    val files = presentation.attachments.size
    val counts =
        stringResource(
            Res.string.reader_status_counts,
            pluralStringResource(Res.plurals.reader_status_replies, replies, replies),
            pluralStringResource(Res.plurals.reader_status_files, files, files),
        )
    val updated =
        if (presentation.lastUpdateText.isBlank()) {
            counts
        } else {
            stringResource(Res.string.reader_status_updated, counts, presentation.lastUpdateText)
        }
    return if (presentation.fromSavedCopy) stringResource(Res.string.reader_status_saved, updated) else updated
}
