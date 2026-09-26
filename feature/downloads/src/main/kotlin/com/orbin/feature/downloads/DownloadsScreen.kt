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
import com.orbin.uinext.NextDestination
import com.orbin.uinext.NextLinearProgress
import com.orbin.uinext.NextScaffold
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ScreenTitle
import com.orbin.uinext.next
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType

/**
 * Download history on the Next language. Transfer + notifications stay with the platform download
 * manager; this screen only lists what Orbin asked for.
 *
 * It is one of the three tabs, so passing [onDestination] draws the tab pill. Clear, and Back when
 * [onBack] is given, live as inline actions above the large title rather than a Material TopAppBar.
 */
@Composable
fun DownloadsScreen(
    onBack: (() -> Unit)? = null,
    onDestination: ((NextDestination) -> Unit)? = null,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    val backLabel = stringResource(R.string.downloads_back)
    val clearLabel = stringResource(R.string.downloads_clear_action)
    val title = stringResource(R.string.downloads_title)

    NextTheme {
        NextScaffold(
            where = title,
            destination = NextDestination.DOWNLOADS.takeIf { onDestination != null },
            onDestination = onDestination,
        ) { bottomPad ->
            DownloadsContent(
                downloads = downloads,
                title = title,
                bottomPad = bottomPad,
                backLabel = backLabel,
                clearLabel = clearLabel,
                onBack = onBack,
                onClear = { showClearDialog = true },
                onRetry = { viewModel.retry(it) },
            )
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
@Suppress("LongParameterList")
private fun DownloadsContent(
    downloads: List<DownloadRecord>,
    title: String,
    bottomPad: PaddingValues,
    backLabel: String,
    clearLabel: String,
    onBack: (() -> Unit)?,
    onClear: () -> Unit,
    onRetry: (Long) -> Unit,
) {
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
            if (onBack != null) {
                InlineAction(
                    label = backLabel,
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = backLabel },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            InlineAction(
                label = clearLabel,
                onClick = onClear,
                modifier = Modifier.semantics { contentDescription = clearLabel },
            )
        }
        if (downloads.isEmpty()) {
            ScreenTitle(
                text = title,
                subtitle = stringResource(R.string.downloads_empty),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LIST_END_SPACE + bottomPad.calculateBottomPadding()),
            ) {
                item { ScreenTitle(text = title) }
                item {
                    GroupedSection {
                        downloads.forEachIndexed { index, record ->
                            DownloadRow(
                                record = record,
                                onRetry = { onRetry(record.id) },
                            )
                            if (index < downloads.lastIndex) GroupedDivider()
                        }
                    }
                }
            }
        }
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
            NextLinearProgress(
                progress = fraction,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Room under the last download, above the tab pill's own clearance. */
private val LIST_END_SPACE = 28.dp

private val ACTIVE_DOWNLOAD_STATUSES = setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING)

@StringRes
private fun DownloadStatus.labelRes(): Int =
    when (this) {
        DownloadStatus.QUEUED -> R.string.downloads_status_queued
        DownloadStatus.RUNNING -> R.string.downloads_status_running
        DownloadStatus.COMPLETED -> R.string.downloads_status_completed
        DownloadStatus.FAILED -> R.string.downloads_status_failed
    }
