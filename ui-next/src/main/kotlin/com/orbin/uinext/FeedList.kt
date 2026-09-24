package com.orbin.uinext

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.NextType

@Composable
internal fun FeedGroupHeading(
    title: String,
    board: String,
    count: Int,
) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (title == board) board else "$title $board",
            style = NextType.title2,
            color = next.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        WidthSpacer(8)
        MetaLine("$count threads")
    }
}
