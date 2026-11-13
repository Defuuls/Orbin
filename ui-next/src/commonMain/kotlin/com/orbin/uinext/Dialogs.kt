package com.orbin.uinext

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_confirm_cancel
import com.orbin.uinext.resources.next_confirm_delete
import org.jetbrains.compose.resources.stringResource

/**
 * Next-language confirmation for irreversible actions.
 *
 * Delegates to [OrbinConfirmDialog] so Downloads, Search, and Settings share the Coral card
 * chrome as the rest of the restyled screens.
 */
@Composable
fun NextConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(Res.string.next_confirm_delete),
    dismissLabel: String = stringResource(Res.string.next_confirm_cancel),
    /** When true, confirm uses the error fill rather than accent (delete / clear). */
    destructive: Boolean = true,
) {
    OrbinConfirmDialog(
        title = title,
        message = message,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmLabel = confirmLabel,
        dismissLabel = dismissLabel,
        destructive = destructive,
    )
}

/** Accessible error color shared by destructive labels and confirmation buttons. */
val nextDestructiveRed: Color
    @Composable get() = next.destructive

/** One row in a [NextActionSheet] or [OrbinActionSheet]: a word, and what it does. */
data class NextSheetAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * A short list of things to do with what was long-pressed.
 *
 * Delegates to [OrbinActionSheet]. Every action closes the sheet after it runs.
 */
@Composable
fun NextActionSheet(
    title: String,
    actions: List<NextSheetAction>,
    onDismiss: () -> Unit,
) {
    OrbinActionSheet(title = title, actions = actions, onDismiss = onDismiss)
}
