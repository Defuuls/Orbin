package com.orbin.ios

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orbin.core.model.DownloadRecord
import com.orbin.core.model.DownloadStatus
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_downloads_clear
import com.orbin.ios.resources.ios_downloads_clear_message
import com.orbin.ios.resources.ios_downloads_clear_title
import com.orbin.ios.resources.ios_downloads_empty
import com.orbin.ios.resources.ios_downloads_retry
import com.orbin.ios.resources.ios_downloads_status_completed
import com.orbin.ios.resources.ios_downloads_status_failed
import com.orbin.ios.resources.ios_downloads_status_queued
import com.orbin.ios.resources.ios_downloads_status_running
import com.orbin.ios.resources.ios_downloads_title
import com.orbin.ios.resources.ios_downloads_where
import com.orbin.uinext.DownloadRow
import com.orbin.uinext.GroupedDivider
import com.orbin.uinext.GroupedSection
import com.orbin.uinext.InlineAction
import com.orbin.uinext.NextConfirmDialog
import com.orbin.uinext.NextDestination
import com.orbin.uinext.NextScaffold
import com.orbin.uinext.ScreenTitle
import com.orbin.uinext.tokens.NextSpace
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Downloads tab: every file saved from a thread, newest first, with live progress, a retry on
 * the ones that failed, and Clear to forget the list. The same layout as Android's Downloads screen,
 * built from the same shared parts.
 */
@Composable
internal fun DownloadsScreen(
    records: List<DownloadRecord>,
    onRetry: (id: Long) -> Unit,
    onClear: () -> Unit,
    onDestination: (NextDestination) -> Unit,
) {
    val title = stringResource(Res.string.ios_downloads_title)
    var confirmClear by remember { mutableStateOf(false) }
    NextScaffold(where = title, destination = NextDestination.DOWNLOADS, onDestination = onDestination) { bottomPad ->
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
        ) {
            if (records.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = NextSpace.gutter - 4.dp, vertical = 4.dp)) {
                    Spacer(Modifier.weight(1f))
                    InlineAction(stringResource(Res.string.ios_downloads_clear), onClick = { confirmClear = true })
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPad.calculateBottomPadding() + LIST_END_SPACE),
            ) {
                item {
                    val subtitle =
                        if (records.isEmpty()) Res.string.ios_downloads_empty else Res.string.ios_downloads_where
                    ScreenTitle(text = title, subtitle = stringResource(subtitle))
                }
                if (records.isNotEmpty()) {
                    item {
                        GroupedSection {
                            records.forEachIndexed { index, record ->
                                DownloadRow(record, onRetry = { onRetry(record.id) })
                                if (index < records.lastIndex) GroupedDivider()
                            }
                        }
                    }
                }
            }
        }
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
        status = stringResource(record.status.label),
        progress = record.progressFraction,
        active = record.status.isActive,
        retryLabel = stringResource(Res.string.ios_downloads_retry),
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

private val LIST_END_SPACE = 28.dp
