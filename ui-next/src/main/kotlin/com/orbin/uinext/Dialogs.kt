package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType

/**
 * Next-language confirmation for irreversible actions.
 *
 * Replaces Material AlertDialog / design-system ModernConfirmDialog so Downloads, Search, and
 * Settings share the same grouped card + InlineAction chrome as the rest of ui-next.
 */
@Composable
fun NextConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.next_confirm_delete),
    dismissLabel: String = stringResource(R.string.next_confirm_cancel),
    /** When true, confirm uses system-red rather than accent (delete / clear). */
    destructive: Boolean = true,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NextRadius.card))
                    .background(next.raised)
                    .padding(horizontal = NextSpace.gutter, vertical = NextSpace.section),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = NextType.title3,
                fontWeight = FontWeight.SemiBold,
                color = next.ink,
            )
            Text(
                text = message,
                style = NextType.body,
                color = next.muted,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineAction(label = dismissLabel, onClick = onDismiss)
                if (destructive) {
                    DestructiveConfirm(label = confirmLabel, onClick = onConfirm)
                } else {
                    InlineAction(label = confirmLabel, accent = true, onClick = onConfirm)
                }
            }
        }
    }
}

@Composable
private fun DestructiveConfirm(
    label: String,
    onClick: () -> Unit,
) {
    val red = if (next.dark) Color(0xFFFF453A) else Color(0xFFFF3B30)
    val shape = RoundedCornerShape(NextRadius.control)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
                .clip(shape)
                .clickable(
                    role = Role.Button,
                    indication = null,
                    interactionSource = interaction,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = NextType.footnote,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier =
                Modifier
                    .clip(shape)
                    .background(red)
                    .padding(horizontal = 13.dp, vertical = 7.dp),
        )
    }
}
