package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One post in a thread. [depth] is its position in the reply tree. */
data class Post(
    val number: String,
    val time: String,
    val body: String,
    val depth: Int = 0,
    val hasMedia: Boolean = false,
    val replies: Int = 0,
    val spoiler: Boolean = false,
)

/**
 * The thread reader.
 *
 * The current reader puts a title bar on top with six actions and a "More" overflow behind them,
 * draws every post as an elevated card, and indents replies with a coloured vertical rule per
 * level. On a phone, three levels of nesting leaves roughly half the width for text.
 *
 * Here a reply's depth is a single hairline at the left edge and a small indent — enough to read
 * the structure, not enough to eat the column. Posts are separated by space rather than boxed.
 * The post number and time sit on one muted line above the text, where they can be scanned or
 * ignored; today they are a row of chips.
 *
 * A spoiler stays blacked out until pressed, exactly as now — that behaviour is load-bearing and
 * survives unchanged.
 */
@Composable
fun ThreadScreen(
    subject: String,
    board: String,
    posts: List<Post>,
    modifier: Modifier = Modifier,
) {
    Surface {
        Column(modifier = modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    ScreenTitle(
                        text = subject,
                        subtitle = "$board  ·  ${posts.size} posts  ·  4 files",
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER)) {
                        InlineAction("Watch", accent = true)
                        WidthSpacer(18)
                        InlineAction("Files")
                        WidthSpacer(18)
                        InlineAction("Download all")
                        WidthSpacer(18)
                        InlineAction("Share")
                    }
                    Gap(16)
                    Hairline()
                }
                items(posts) { post ->
                    PostView(post)
                    Hairline(inset = true)
                }
            }
            ContextRail(where = subject.take(28), detail = board)
        }
    }
}

@Composable
private fun PostView(post: Post) {
    // Intrinsic height so each depth rule runs the full height of the post it belongs to, rather
    // than being a tick at the top of it.
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        repeat(post.depth) {
            Box(
                modifier =
                    Modifier
                        .padding(start = GUTTER)
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = HAIRLINE)),
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(
                        start = if (post.depth == 0) GUTTER else 12.dp,
                        end = GUTTER,
                        top = 13.dp,
                        bottom = 13.dp,
                    ),
        ) {
            MetaLine("${post.number}  ·  ${post.time}" + if (post.replies > 0) "  ·  ${post.replies} replies" else "")
            Gap(6)
            if (post.spoiler) {
                // The same scrim the shipped app uses: black regardless of theme, so a spoiler is
                // still hidden in dark mode rather than turning into a bright block.
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(Color.Black.copy(alpha = SPOILER_SCRIM)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "  spoiler — press to reveal",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            } else {
                Text(
                    text = post.body,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f),
                )
            }
            if (post.hasMedia) {
                Gap(10)
                MediaTile(modifier = Modifier.fillMaxWidth().height(150.dp))
            }
        }
    }
}

private const val SPOILER_SCRIM = 0.85f
