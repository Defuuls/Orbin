package com.orbin.app.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orbin.app.R
import com.orbin.core.model.AppUpdateState
import com.orbin.uinext.NextLinearProgress
import com.orbin.uinext.OrbinDialog
import com.orbin.uinext.OrbinDialogMessage
import com.orbin.uinext.OrbinDialogTitle
import com.orbin.uinext.OrbinFilledButton
import com.orbin.uinext.OrbinTextButton
import kotlin.math.roundToInt

/** What each button in the update dialog does. */
class UpdateDialogActions(
    val onUpdate: () -> Unit,
    val onDismiss: () -> Unit,
    val onCancel: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onInstall: () -> Unit,
)

/** The update dialog for [state]; draws nothing while there is nothing to say. */
@Composable
fun UpdateDialog(
    state: AppUpdateState,
    actions: UpdateDialogActions,
) {
    when (state) {
        AppUpdateState.Idle -> Unit
        is AppUpdateState.Available ->
            UpdateCard(
                title = stringResource(R.string.update_available_title, state.release.name),
                message = stringResource(R.string.update_available_message),
                dismissLabel = stringResource(R.string.update_action_not_now),
                onDismiss = actions.onDismiss,
                confirmLabel = stringResource(R.string.update_action_update),
                onConfirm = actions.onUpdate,
            )
        is AppUpdateState.Downloading ->
            UpdateCard(
                title = stringResource(R.string.update_downloading_title, state.release.name),
                message =
                    state.progress?.let {
                        stringResource(R.string.update_downloading_progress, (it * PERCENT).roundToInt())
                    } ?: stringResource(R.string.update_downloading_starting),
                dismissLabel = stringResource(R.string.update_action_cancel),
                onDismiss = actions.onCancel,
                // A tap outside must not silently abandon a download the reader asked for.
                dismissOnOutsideTap = false,
            ) {
                NextLinearProgress(progress = state.progress)
            }
        is AppUpdateState.NeedsInstallPermission ->
            UpdateCard(
                title = stringResource(R.string.update_permission_title),
                message = stringResource(R.string.update_permission_message),
                dismissLabel = stringResource(R.string.update_action_open_settings),
                onDismiss = actions.onOpenSettings,
                confirmLabel = stringResource(R.string.update_action_install),
                onConfirm = actions.onInstall,
                onOutsideTap = actions.onDismiss,
            )
        is AppUpdateState.Failed ->
            UpdateCard(
                title = stringResource(R.string.update_failed_title),
                message = state.message,
                dismissLabel = stringResource(R.string.update_action_close),
                onDismiss = actions.onDismiss,
                confirmLabel = stringResource(R.string.update_action_retry),
                onConfirm = actions.onUpdate,
            )
    }
}

/** The Coral card every update and local-data notice uses, with room for a progress bar. */
@Composable
internal fun UpdateCard(
    title: String,
    message: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    onOutsideTap: () -> Unit = onDismiss,
    dismissOnOutsideTap: Boolean = true,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    OrbinDialog(
        onDismissRequest = onOutsideTap,
        dismissOnBackPress = dismissOnOutsideTap,
        dismissOnClickOutside = dismissOnOutsideTap,
    ) {
        OrbinDialogTitle(title)
        OrbinDialogMessage(message)
        extra()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbinTextButton(label = dismissLabel, onClick = onDismiss)
            if (confirmLabel != null) {
                OrbinFilledButton(label = confirmLabel, onClick = onConfirm)
            }
        }
    }
}

private const val PERCENT = 100
