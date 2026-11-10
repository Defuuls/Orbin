package com.orbin.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.social_feed_sources
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

/** Home's source choice belongs to its title menu, leaving the first content row for boards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedSourceSheet(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = next.background,
        contentColor = next.ink,
        tonalElevation = 0.dp,
    ) {
        Text(
            stringResource(Res.string.social_feed_sources),
            style = NextType.titleMedium,
            color = next.ink,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp),
        )
        Hairline()
        labels.forEachIndexed { index, label ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .selectable(selected = index == selected, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(horizontal = GUTTER, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = NextType.body, color = next.ink, modifier = Modifier.weight(1f))
                if (index ==
                    selected
                ) {
                    Icon(ReaderIcons.Check, null, tint = next.ink, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.size(16.dp))
    }
}
