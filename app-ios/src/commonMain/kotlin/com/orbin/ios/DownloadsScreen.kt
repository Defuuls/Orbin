package com.orbin.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.orbin.core.model.DownloadRecord
import com.orbin.core.model.DownloadStatus
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_downloads_clear
import com.orbin.ios.resources.ios_downloads_clear_message
import com.orbin.ios.resources.ios_downloads_clear_title
import com.orbin.ios.resources.ios_downloads_empty
import com.orbin.ios.resources.ios_downloads_empty_title
import com.orbin.ios.resources.ios_downloads_retry
import com.orbin.ios.resources.ios_downloads_retry_description
import com.orbin.ios.resources.ios_downloads_status_completed
import com.orbin.ios.resources.ios_downloads_status_failed
import com.orbin.ios.resources.ios_downloads_status_queued
import com.orbin.ios.resources.ios_downloads_status_running
import com.orbin.ios.resources.ios_downloads_title
import com.orbin.ios.resources.ios_downloads_where
import com.orbin.uinext.DownloadRow
import com.orbin.uinext.DownloadTags
import com.orbin.uinext.NextConfirmDialog
import com.orbin.uinext.NextDestination
import com.orbin.uinext.ReaderEmptyState
import com.orbin.uinext.ReaderMenuItem
import com.orbin.uinext.ReaderScaffold
import com.orbin.uinext.reader
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderSpace
import com.orbin.uinext.withReaderFont
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Downloads: every file saved from a thread, newest first, with live progress, a retry on
 * the ones that failed, and Clear to forget the list. The same layout as Android's Downloads screen,
 * built from the same shared parts.
 * A supplied [bottomBar] owns its system-bottom safe inset.
 */
@Composable
internal fun DownloadsScreen(
    records: List<DownloadRecord>,
    onRetry: (id: Long) -> Unit,
    onClear: () -> Unit,
    onDestination: (NextDestination) -> Unit,
    onBack: (() -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
) {
    val title = stringResource(Res.string.ios_downloads_title)
    var confirmClear by remember { mutableStateOf(false) }
    val clearLabel = stringResource(Res.string.ios_downloads_clear)
    Column(Modifier.fillMaxSize()) {
        ReaderScaffold(
            title = title,
            modifier = Modifier.weight(1f),
            destination = NextDestination.DOWNLOADS,
            onDestination = onDestination,
            onBack = onBack,
            overflow =
                if (records.isNotEmpty()) {
                    listOf(ReaderMenuItem(clearLabel) { confirmClear = true })
                } else {
                    emptyList()
                },
        ) { bottomPad ->
            if (records.isEmpty()) {
                ReaderEmptyState(
                    title = stringResource(Res.string.ios_downloads_empty_title),
                    message = stringResource(Res.string.ios_downloads_empty),
                )
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .testTag(DownloadTags.LIST),
                    contentPadding =
                        PaddingValues(
                            start = ReaderSpace.gutter,
                            end = ReaderSpace.gutter,
                            top = 8.dp,
                            bottom =
                                LIST_END_SPACE + if (bottomBar == null) bottomPad.calculateBottomPadding() else 0.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "downloads-summary", contentType = "summary") {
                        Text(
                            stringResource(Res.string.ios_downloads_where),
                            style = OrbinType.stateBody.withReaderFont(),
                            color = reader.inkMuted,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(records, key = { it.id }, contentType = { "download" }) { record ->
                        DownloadRow(record, onRetry = { onRetry(record.id) })
                    }
                }
            }
        }
        bottomBar?.invoke()
    }
    if (confirmClear) {
        NextConfirmDialog(
            title = stringResource(Res.string.ios_downloads_clear_title),
            message = stringResource(Res.string.ios_downloads_clear_message),
            onConfirm = {
                confirmClear = false
                onClear()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun DownloadRow(
    record: DownloadRecord,
    onRetry: () -> Unit,
) {
    DownloadRow(
        fileName = record.fileName,
        modifier = Modifier.testTag(DownloadTags.row(record.id)),
        status = stringResource(record.status.label),
        progress = record.progressFraction,
        active = record.status.isActive,
        retryLabel = stringResource(Res.string.ios_downloads_retry),
        retryDescription = stringResource(Res.string.ios_downloads_retry_description),
        onRetry = onRetry.takeIf { record.status == DownloadStatus.FAILED },
    )
}

private val DownloadStatus.label: StringResource
    get() =
        when (this) {
            DownloadStatus.QUEUED -> Res.string.ios_downloads_status_queued
            DownloadStatus.RUNNING -> Res.string.ios_downloads_status_running
            DownloadStatus.COMPLETED -> Res.string.ios_downloads_status_completed
            DownloadStatus.FAILED -> Res.string.ios_downloads_status_failed
        }

private val LIST_END_SPACE = ReaderSpace.gutter
