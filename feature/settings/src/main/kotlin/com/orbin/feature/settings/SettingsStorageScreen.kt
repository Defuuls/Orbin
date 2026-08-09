package com.orbin.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.designsystem.component.ModernListItem
import com.orbin.core.designsystem.component.ModernSmallTopAppBar
import com.orbin.core.model.DownloadOrganization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEFAULT_BACKUP_FILE_NAME = "orbin-backup.json"
private val IMAGE_CACHE_LIMITS_MB = listOf(128, 256, 512, 1024)

/** Downloads, the saved-media folder, image cache sizing, and backup export/import. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsStorageScreen(
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                }
                viewModel.setDownloadFolderUri(uri.toString())
            }
        }
    val backupExporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                viewModel.exportBackup(appVersionName(context)) { backupJson ->
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(backupJson.toByteArray()) }
                            ?: error("Could not open the selected file for writing")
                    }
                }
            }
        }
    val backupImporter =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                viewModel.importBackup {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                            ?: error("Could not open the selected file for reading")
                    }
                }
            }
        }

    LaunchedEffect(backupStatus) {
        val status = backupStatus ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(status.message())
        viewModel.clearBackupStatus()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ModernSmallTopAppBar(
                title = "Storage & Backup",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
        ) {
            ModernListItem(
                title = "Downloads",
                subtitle = "View download history",
                onClick = onOpenDownloads,
            )
            ModernListItem(
                title = "Saved media folder",
                subtitle = settings.downloadFolderUri.ifBlank { "Downloads/Orbin" },
                onClick = { folderPicker.launch(null) },
            )
            ChoiceRow(
                label = "Download folder structure",
                values = DownloadOrganization.entries,
                selected = settings.downloadOrganization,
                text = { it.label },
                onChange = viewModel::setDownloadOrganization,
            )
            ChipChoiceRow(
                label = "Image cache limit",
                values = IMAGE_CACHE_LIMITS_MB,
                selected = settings.imageCacheLimitMb,
                text = { "$it MB" },
                onChange = viewModel::setImageCacheLimitMb,
            )
            ModernListItem(
                title = "Export data",
                subtitle = "Save settings, boards and bookmarks to a file",
                onClick = { backupExporter.launch(DEFAULT_BACKUP_FILE_NAME) },
            )
            ModernListItem(
                title = "Import data",
                subtitle = "Restore settings, boards and bookmarks from a backup",
                onClick = { backupImporter.launch(arrayOf("application/json", "*/*")) },
            )
        }
    }
}

private fun BackupStatus.message(): String =
    when (this) {
        BackupStatus.Exported -> "Backup saved"
        is BackupStatus.Imported ->
            "Restored ${summary.subscribedBoards} boards, ${summary.bookmarks} bookmarks and " +
                "${summary.savedSearches} saved searches" +
                if (summary.skippedUnknownProvider > 0) {
                    " (skipped ${summary.skippedUnknownProvider} from a provider this build doesn't support)"
                } else {
                    ""
                }
        is BackupStatus.Failed -> message
    }
