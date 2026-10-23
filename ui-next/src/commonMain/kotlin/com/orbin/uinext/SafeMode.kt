package com.orbin.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.NextType

/** What the safe-mode screen says, in the platform's own strings. */
data class SafeModeText(
    val title: String,
    val explanation: String,
    val saveFirst: String,
    val saveCrashDetails: String,
    val reset: String,
    val tryNormally: String,
    val resetDialogTitle: String,
    val resetDialogText: String,
    val resetConfirm: String,
    val cancel: String,
)

/**
 * Shown instead of the app when consecutive launches have crashed during startup, on Android and
 * iOS alike.
 *
 * Save the evidence first, then reset only if that is what it takes.
 */
@Composable
fun SafeModeScreen(
    text: SafeModeText,
    onExportDiagnostics: () -> Unit,
    onResetLocalData: () -> Unit,
    onContinueAnyway: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showResetConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text.title, style = NextType.title2, color = next.ink)
        Text(text.explanation, style = NextType.body, color = next.muted)
        Text(text.saveFirst, style = NextType.body, color = next.muted)

        InlineAction(label = text.saveCrashDetails, accent = true, onClick = onExportDiagnostics)
        InlineAction(label = text.reset, onClick = { showResetConfirmation = true })
        InlineAction(label = text.tryNormally, onClick = onContinueAnyway)
    }

    if (showResetConfirmation) {
        NextConfirmDialog(
            title = text.resetDialogTitle,
            message = text.resetDialogText,
            onConfirm = {
                showResetConfirmation = false
                onResetLocalData()
            },
            onDismiss = { showResetConfirmation = false },
            confirmLabel = text.resetConfirm,
            dismissLabel = text.cancel,
        )
    }
}
