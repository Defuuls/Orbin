package com.orbin.feature.downloads

import androidx.annotation.StringRes
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.DownloadRecord
import com.orbin.core.model.DownloadStatus
import com.orbin.uinext.GroupedDivider
import com.orbin.uinext.GroupedSection
import com.orbin.uinext.InlineAction
import com.orbin.uinext.MetaLine
import com.orbin.uinext.NextConfirmDialog
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ScreenTitle
import com.orbin.uinext.next
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType

/**
 * Download history on the Next language. Transfer + notifications stay with the platform download
 * manager; this screen only lists what Orbin asked for.
 *
 * Hosted under [com.orbin.uinext.NextChromeHost] in the nav graph, so chrome is the ContextRail —
 * Back / Clear live as inline actions under the large title rather than a Material TopAppBar.
 */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    val backLabel = stringResource(R.string.downloads_back)
    val clearLabel = stringResource(R.string.downloads_clear_action)

    NextTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NextSpace.gutter - 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineAction(
                    label = backLabel,
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = backLabel },
                )
                Spacer(modifier = Modifier.weight(1f))
                InlineAction(
                    label = clearLabel,
                    onClick = { showClearDialog = true },
                    modifier = Modifier.semantics { contentDescription = clearLabel },
                )
            }
            if (downloads.isEmpty()) {
                ScreenTitle(
                    text = stringResource(R.string.downloads_title),
                    subtitle = stringResource(R.string.downloads_empty),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    item { ScreenTitle(text = stringResource(R.string.downloads_title)) }
                    item {
                        GroupedSection {
                            downloads.forEachIndexed { index, record ->
                                DownloadRow(
                                    record = record,
                                    onRetry = { viewModel.retry(record.id) },
                                )
                                if (index < downloads.lastIndex) GroupedDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        NextConfirmDialog(
            title = stringResource(R.string.downloads_clear_dialog_title),
            message = stringResource(R.string.downloads_clear_dialog_text),
            onConfirm = {
                viewModel.clear()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false },
        )
    }
}

@Composable
private fun DownloadRow(
    record: DownloadRecord,
    onRetry: () -> Unit,
) {
    val retryLabel = stringResource(R.string.downloads_retry)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = NextSpace.rowX, vertical = NextSpace.rowY),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.fileName,
                style = NextType.body,
                fontWeight = FontWeight.Medium,
                color = next.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DownloadProgress(record)
        }
        if (record.status == DownloadStatus.FAILED) {
            InlineAction(
                label = "Retry",
                accent = true,
                onClick = onRetry,
                modifier = Modifier.semantics { contentDescription = retryLabel },
            )
        }
    }
}

@Composable
private fun DownloadProgress(record: DownloadRecord) {
    val fraction = record.progressFraction
    Column(modifier = Modifier.padding(top = 4.dp)) {
        MetaLine(
            if (fraction != null && record.status in ACTIVE_DOWNLOAD_STATUSES) {
                "${stringResource(record.status.labelRes())} · ${(fraction * 100).toInt()}%"
            } else {
                stringResource(record.status.labelRes())
            },
        )
        if (record.status in ACTIVE_DOWNLOAD_STATUSES) {
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    color = next.accent,
                    trackColor = next.hairline,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    color = next.accent,
                    trackColor = next.hairline,
                )
            }
        }
    }
}

private val ACTIVE_DOWNLOAD_STATUSES = setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING)

@StringRes
private fun DownloadStatus.labelRes(): Int =
    when (this) {
        DownloadStatus.QUEUED -> R.string.downloads_status_queued
        DownloadStatus.RUNNING -> R.string.downloads_status_running
        DownloadStatus.COMPLETED -> R.string.downloads_status_completed
        DownloadStatus.FAILED -> R.string.downloads_status_failed
    }
