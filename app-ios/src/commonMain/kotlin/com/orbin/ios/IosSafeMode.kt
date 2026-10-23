package com.orbin.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_safe_mode_cancel
import com.orbin.ios.resources.ios_safe_mode_explanation
import com.orbin.ios.resources.ios_safe_mode_no_details
import com.orbin.ios.resources.ios_safe_mode_reset
import com.orbin.ios.resources.ios_safe_mode_reset_confirm
import com.orbin.ios.resources.ios_safe_mode_reset_dialog_text
import com.orbin.ios.resources.ios_safe_mode_reset_dialog_title
import com.orbin.ios.resources.ios_safe_mode_save_crash_details
import com.orbin.ios.resources.ios_safe_mode_save_first
import com.orbin.ios.resources.ios_safe_mode_title
import com.orbin.ios.resources.ios_safe_mode_try_normally
import com.orbin.uinext.NextPlatform
import com.orbin.uinext.NextTheme
import com.orbin.uinext.SafeModeScreen
import com.orbin.uinext.SafeModeText
import com.orbin.uinext.next
import org.jetbrains.compose.resources.stringResource

/**
 * Android's safe mode, on iOS: shown instead of the app after two startup crashes in a row, in the
 * system's theme because the reader's own is local state. "Save crash details" offers the recorded
 * crashes to the share sheet, where they can go to Files or a message.
 */
@Composable
internal fun IosSafeMode(
    report: () -> String?,
    onReset: () -> Unit,
    onContinue: () -> Unit,
) {
    NextTheme(platform = NextPlatform.IOS) {
        val noDetails = stringResource(Res.string.ios_safe_mode_no_details)
        Box(Modifier.fillMaxSize().background(next.background).safeDrawingPadding()) {
            SafeModeScreen(
                text =
                    SafeModeText(
                        title = stringResource(Res.string.ios_safe_mode_title),
                        explanation = stringResource(Res.string.ios_safe_mode_explanation),
                        saveFirst = stringResource(Res.string.ios_safe_mode_save_first),
                        saveCrashDetails = stringResource(Res.string.ios_safe_mode_save_crash_details),
                        reset = stringResource(Res.string.ios_safe_mode_reset),
                        tryNormally = stringResource(Res.string.ios_safe_mode_try_normally),
                        resetDialogTitle = stringResource(Res.string.ios_safe_mode_reset_dialog_title),
                        resetDialogText = stringResource(Res.string.ios_safe_mode_reset_dialog_text),
                        resetConfirm = stringResource(Res.string.ios_safe_mode_reset_confirm),
                        cancel = stringResource(Res.string.ios_safe_mode_cancel),
                    ),
                onExportDiagnostics = { shareContent(report() ?: noDetails) },
                onResetLocalData = onReset,
                onContinueAnyway = onContinue,
            )
        }
    }
}
