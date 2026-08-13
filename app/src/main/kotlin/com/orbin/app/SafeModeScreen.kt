package com.orbin.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shown instead of the app when consecutive launches have crashed during startup.
 *
 * The 82-Alioth crash left users with an app that died on every launch and no way to act on it
 * short of reinstalling, which would have thrown away their data anyway. This screen is the way
 * out: save the evidence first, then reset only if that is what it takes.
 */
@Composable
fun SafeModeScreen(
    onExportDiagnostics: () -> Unit,
    onResetLocalData: () -> Unit,
    onContinueAnyway: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showResetConfirmation by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Orbin didn't start", style = MaterialTheme.typography.headlineSmall)
            Text(
                "The last two launches stopped before the app was usable, so it's starting in " +
                    "safe mode instead of trying again.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Saving the crash details first is worth doing even if you plan to reset — it's " +
                    "the only record of what went wrong, and it stays on your device until you " +
                    "share it.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(onClick = onExportDiagnostics) { Text("Save crash details") }

            OutlinedButton(onClick = { showResetConfirmation = true }) { Text("Reset local data") }

            TextButton(onClick = onContinueAnyway) { Text("Try starting normally") }
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Reset local data?") },
            text = {
                Text(
                    "Deletes bookmarks, reading history, saved searches and download history from " +
                        "this device. Your settings are kept. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirmation = false
                    onResetLocalData()
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}
