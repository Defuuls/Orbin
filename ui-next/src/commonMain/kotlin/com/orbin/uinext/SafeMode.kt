package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.OrbinType

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
                .background(reader.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        OrbinLogo(size = 48.dp, contentDescription = null)
        Text(
            text.title,
            style = OrbinType.stateTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
            color = reader.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(text.explanation, style = OrbinType.stateBody.withReaderFont(), color = reader.inkMuted)
        Text(text.saveFirst, style = OrbinType.stateBody.withReaderFont(), color = reader.inkMuted)

        OrbinFilledButton(
            label = text.saveCrashDetails,
            onClick = onExportDiagnostics,
            modifier = Modifier.fillMaxWidth(),
        )
        OrbinTextButton(
            label = text.reset,
            onClick = { showResetConfirmation = true },
            modifier = Modifier.fillMaxWidth(),
        )
        OrbinTextButton(
            label = text.tryNormally,
            onClick = onContinueAnyway,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showResetConfirmation) {
        OrbinConfirmDialog(
            title = text.resetDialogTitle,
            message = text.resetDialogText,
            onConfirm = {
                showResetConfirmation = false
                onResetLocalData()
            },
            onDismiss = { showResetConfirmation = false },
            confirmLabel = text.resetConfirm,
            dismissLabel = text.cancel,
            destructive = true,
        )
    }
}
