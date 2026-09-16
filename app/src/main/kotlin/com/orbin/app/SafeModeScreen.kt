package com.orbin.app

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orbin.uinext.InlineAction
import com.orbin.uinext.NextConfirmDialog
import com.orbin.uinext.NextTheme
import com.orbin.uinext.next
import com.orbin.uinext.tokens.NextType

/**
 * Shown instead of the app when consecutive launches have crashed during startup.
 *
 * Save the evidence first, then reset only if that is what it takes — drawn in the Next language.
 */
@Composable
fun SafeModeScreen(
    onExportDiagnostics: () -> Unit,
    onResetLocalData: () -> Unit,
    onContinueAnyway: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showResetConfirmation by remember { mutableStateOf(false) }

    NextTheme {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.safe_mode_title), style = NextType.title2, color = next.ink)
            Text(
                stringResource(R.string.safe_mode_explanation),
                style = NextType.body,
                color = next.muted,
            )
            Text(
                stringResource(R.string.safe_mode_save_first),
                style = NextType.body,
                color = next.muted,
            )

            InlineAction(
                label = stringResource(R.string.safe_mode_save_crash_details),
                accent = true,
                onClick = onExportDiagnostics,
            )
            InlineAction(
                label = stringResource(R.string.safe_mode_reset),
                onClick = { showResetConfirmation = true },
            )
            InlineAction(
                label = stringResource(R.string.safe_mode_try_normally),
                onClick = onContinueAnyway,
            )
        }

        if (showResetConfirmation) {
            NextConfirmDialog(
                title = stringResource(R.string.safe_mode_reset_dialog_title),
                message = stringResource(R.string.safe_mode_reset_dialog_text),
                onConfirm = {
                    showResetConfirmation = false
                    onResetLocalData()
                },
                onDismiss = { showResetConfirmation = false },
                confirmLabel = stringResource(R.string.safe_mode_reset_confirm),
                dismissLabel = stringResource(R.string.safe_mode_cancel),
            )
        }
    }
}
