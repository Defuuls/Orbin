package com.orbin.uinext

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_threads_count
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.pluralStringResource

/** Optional grouping remains available to callers that need a board-specific reading mode. */
@Composable
internal fun FeedGroupHeading(
    title: String,
    board: String,
    count: Int,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 12.dp)) {
        Text(
            if (title == board) board else "$title $board",
            style = NextType.titleMedium,
            color = next.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            pluralStringResource(Res.plurals.next_threads_count, count, count),
            style = NextType.footnote,
            color = next.muted,
        )
    }
}
