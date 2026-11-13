package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_confirm_cancel
import com.orbin.uinext.resources.next_confirm_delete
import com.orbin.uinext.tokens.NextMaterials
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.OrbinElevation
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.ReaderType
import org.jetbrains.compose.resources.stringResource

/**
 * Shared Orbin Coral dialog chrome: a softly elevated [OrbinCard] over the platform scrim.
 *
 * What's New, the update dialog, the local-data notice, and confirmations share this so dark-mode
 * cards use [reader.surfaceVariant] against the page ground and never disappear. [NextConfirmDialog]
 * and [NextActionSheet] delegate here.
 */
@Composable
fun OrbinDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                dismissOnBackPress = dismissOnBackPress,
                dismissOnClickOutside = dismissOnClickOutside,
                usePlatformDefaultWidth = true,
            ),
    ) {
        OrbinCard(modifier = modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** A dialog heading in reader title type. Wraps at large font scale. */
@Composable
fun OrbinDialogTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = ReaderType.appBarTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
        color = reader.ink,
        modifier = modifier,
    )
}

/** Supporting copy under a dialog title. */
@Composable
fun OrbinDialogMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = ReaderType.body.withReaderFont(),
        color = reader.inkMuted,
        modifier = modifier,
    )
}

/**
 * A filled Coral action: coral by default, [reader.error] when [destructive]. The target is at
 * least [MIN_TOUCH_TARGET] and grows with the label at 200% font. This is not a posting control.
 */
@Composable
fun OrbinFilledButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val fill =
        when {
            !enabled -> reader.surfaceVariant
            destructive -> reader.error
            else -> reader.accent
        }
    val ink =
        when {
            !enabled -> reader.inkMuted
            destructive -> next.onDestructive
            else -> reader.onAccent
        }
    val shape = RoundedCornerShape(OrbinRadius.pill)
    Box(
        modifier =
            modifier
                .heightIn(min = MIN_TOUCH_TARGET)
                .clip(shape)
                .background(fill)
                .nextClickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = ReaderType.label.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
            color = ink,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A text-only Coral action with a 48dp target and no outline. Used for dismiss and secondary
 * choices next to [OrbinFilledButton].
 */
@Composable
fun OrbinTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .heightIn(min = MIN_TOUCH_TARGET)
                .clip(RoundedCornerShape(OrbinRadius.pill))
                .nextClickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = ReaderType.label.withReaderFont().copy(fontWeight = FontWeight.Medium),
            color = if (enabled) reader.accent else reader.inkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Coral confirmation for an irreversible action, matching [NextConfirmDialog]'s contract so
 * existing callers keep their labels and destructive default.
 */
@Composable
fun OrbinConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(Res.string.next_confirm_delete),
    dismissLabel: String = stringResource(Res.string.next_confirm_cancel),
    destructive: Boolean = true,
) {
    OrbinDialog(onDismissRequest = onDismiss) {
        OrbinDialogTitle(title)
        OrbinDialogMessage(message)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbinTextButton(label = dismissLabel, onClick = onDismiss)
            OrbinFilledButton(
                label = confirmLabel,
                onClick = onConfirm,
                destructive = destructive,
            )
        }
    }
}

/**
 * A short list of things to do with what was long-pressed, in Coral chrome.
 *
 * The sheet fill is an [OrbinCard] colour (white on light, [reader.surfaceVariant] on dark) so it
 * lifts off the page without a hairline outline. Each row is a 48dp target and wraps at 200% font.
 * Every action closes the sheet after it runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbinActionSheet(
    title: String,
    actions: List<NextSheetAction>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ios = LocalNextPlatform.current == NextPlatform.IOS
    val shape = RoundedCornerShape(topStart = OrbinRadius.panel, topEnd = OrbinRadius.panel)
    val fill = if (next.dark) reader.surfaceVariant else reader.background
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = fill,
        contentColor = reader.ink,
        tonalElevation = if (next.dark) OrbinElevation.card else 0.dp,
        shape = shape,
        scrimColor = Color.Black.copy(alpha = NextMaterials.SCRIM),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = NextSpace.sheetHandleTop, bottom = 8.dp)
                    .width(if (ios) 36.dp else 32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(OrbinRadius.pill))
                    .background(reader.inkMuted.copy(alpha = 0.45f)),
            )
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = ReaderType.meta.withReaderFont(),
                color = reader.inkMuted,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            actions.forEach { action ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = MIN_TOUCH_TARGET)
                            .clip(RoundedCornerShape(OrbinRadius.thumbnail))
                            .nextClickable(
                                role = Role.Button,
                                onClick = {
                                    action.onClick()
                                    onDismiss()
                                },
                            ).padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = action.label,
                        style = ReaderType.body.withReaderFont().copy(fontWeight = FontWeight.Medium),
                        color = reader.ink,
                    )
                }
            }
        }
    }
}
