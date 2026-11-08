package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_threads_count
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.pluralStringResource

@Composable
internal fun FeedGroupHeading(
    title: String,
    board: String,
    count: Int,
) {
    val badgeShape = RoundedCornerShape(NextRadius.tight)
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(boardHue(board)),
        )
        WidthSpacer(12)
        Column(Modifier.weight(1f)) {
            Text(
                if (title == board) board else "$title $board",
                style = NextType.title2,
                fontWeight = FontWeight.Bold,
                color = next.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Gap(6)
            Box(
                Modifier
                    .clip(badgeShape)
                    .background(next.raised)
                    .border(1.dp, next.hairline, badgeShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                MetaLine(pluralStringResource(Res.plurals.next_threads_count, count, count))
            }
        }
    }
}
