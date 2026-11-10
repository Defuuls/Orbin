package com.orbin.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.collection_back
import com.orbin.uinext.resources.collection_empty
import com.orbin.uinext.resources.collection_settings
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

/** A locally saved thread, carrying its complete source identity in [id] and [source]. */
@Immutable
data class ThreadCollectionRow(
    val id: String,
    val title: String,
    val source: String,
    val detail: String = "",
)

/** Saved, watched and history share one accessible directory over real local records. */
@Composable
fun ThreadCollectionScreen(
    title: String,
    rows: List<ThreadCollectionRow>,
    onOpen: (ThreadCollectionRow) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onDestination: ((NextDestination) -> Unit)? = null,
    selectedDestination: NextDestination = NextDestination.SAVED,
) {
    NextScaffold(
        where = title,
        modifier = modifier,
        destination = selectedDestination.takeIf { onDestination != null },
        onDestination = onDestination,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    NextIconAction(
                        ReaderIcons.Back,
                        stringResource(Res.string.collection_back),
                        onBack,
                    )
                }
                Text(title, style = NextType.titleLarge, color = next.ink, modifier = Modifier.padding(8.dp))
                Spacer(Modifier.weight(1f))
                if (onSettings != null) {
                    NextIconAction(ReaderIcons.Menu, stringResource(Res.string.collection_settings), onSettings)
                }
            }
            if (rows.isEmpty()) {
                NextEmpty(stringResource(Res.string.collection_empty))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp),
                ) {
                    items(rows, key = { it.id }) { row ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .nextClickable(role = Role.Button) { onOpen(row) }
                                .padding(horizontal = NextSpace.gutter, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(row.source, style = NextType.footnote, color = next.muted)
                            Text(row.title, style = NextType.body, color = next.ink)
                            if (row.detail.isNotBlank()) Text(row.detail, style = NextType.footnote, color = next.muted)
                        }
                    }
                }
            }
        }
    }
}
