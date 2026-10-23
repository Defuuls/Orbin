package com.orbin.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.orbin.uinext.NextTheme
import com.orbin.uinext.SafeModeText
import com.orbin.uinext.SafeModeScreen as SharedSafeModeScreen

/** Shown instead of the app when consecutive launches have crashed during startup: the shared screen. */
@Composable
fun SafeModeScreen(
    onExportDiagnostics: () -> Unit,
    onResetLocalData: () -> Unit,
    onContinueAnyway: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NextTheme {
        SharedSafeModeScreen(
            text =
                SafeModeText(
                    title = stringResource(R.string.safe_mode_title),
                    explanation = stringResource(R.string.safe_mode_explanation),
                    saveFirst = stringResource(R.string.safe_mode_save_first),
                    saveCrashDetails = stringResource(R.string.safe_mode_save_crash_details),
                    reset = stringResource(R.string.safe_mode_reset),
                    tryNormally = stringResource(R.string.safe_mode_try_normally),
                    resetDialogTitle = stringResource(R.string.safe_mode_reset_dialog_title),
                    resetDialogText = stringResource(R.string.safe_mode_reset_dialog_text),
                    resetConfirm = stringResource(R.string.safe_mode_reset_confirm),
                    cancel = stringResource(R.string.safe_mode_cancel),
                ),
            onExportDiagnostics = onExportDiagnostics,
            onResetLocalData = onResetLocalData,
            onContinueAnyway = onContinueAnyway,
            modifier = modifier,
        )
    }
}
