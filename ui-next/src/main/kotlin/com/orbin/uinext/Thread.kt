package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * draws every post as an elevated card, and indents replies with a coloured vertical rule per level.
 * On a phone, three levels of nesting leaves roughly half the width for text.
 *
 * Here a reply's depth is one hairline in the board's colour and a small indent — enough to read the
 * structure, not enough to eat the column. Posts are separated by space rather than boxed. The post
 * number and time sit on one quiet line above the text, where they can be scanned or ignored; today
 * they are a row of chips.
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
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(bottom = RAIL_HEIGHT + 28.dp)) {
                item {
                    Row(
                        modifier = Modifier.padding(start = GUTTER, top = 26.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BoardDot(board, size = 6.dp)
                        WidthSpacer(7)
                        Text(
                            text = board,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp,
                            color = boardHue(board),
                        )
                        WidthSpacer(8)
                        MetaLine("${posts.size} posts  ·  4 files", color = next.faint)
                    }
                    ScreenTitle(text = subject, size = 26)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER - 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InlineAction("Watching", accent = true)
                        WidthSpacer(4)
                        InlineAction("Files")
                        WidthSpacer(4)
                        InlineAction("Download all")
                        WidthSpacer(4)
                        InlineAction("Share")
                    }
                    Gap(18)
                    Hairline()
                }
                itemsIndexed(posts) { index, post ->
                    PostView(post, board = board, seed = index)
                    if (index < posts.lastIndex) Hairline(inset = true)
                }
            }
            ContextRail(
                where = subject.take(26),
                detail = board,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun PostView(
    post: Post,
    board: String,
    seed: Int,
) {
    // Intrinsic height so each depth rule runs the full height of the post it belongs to, rather
    // than being a tick at the top of it.
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        repeat(post.depth) {
            Box(
                modifier =
                    Modifier
                        .padding(start = GUTTER, top = 14.dp, bottom = 14.dp)
                        .fillMaxHeight()
                        .width(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(boardHue(board).copy(alpha = 0.30f)),
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(
                        start = if (post.depth == 0) GUTTER else 13.dp,
                        end = GUTTER,
                        top = 15.dp,
                        bottom = 15.dp,
                    ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetaLine(post.number, color = next.faint)
                WidthSpacer(8)
                MetaLine(post.time, color = next.faint)
                if (post.replies > 0) {
                    WidthSpacer(8)
                    Pill("${post.replies} replies", tint = next.muted)
                }
            }
            Gap(8)
            if (post.spoiler) {
                // The same scrim the shipped app uses: black regardless of theme, so a spoiler is
                // still hidden in dark mode rather than turning into a bright block.
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = SPOILER_SCRIM)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "Spoiler — press to reveal",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
            } else {
                Text(
                    text = post.body,
                    fontSize = 15.5.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Normal,
                    color = next.ink.copy(alpha = 0.90f),
                )
            }
            if (post.hasMedia) {
                Gap(12)
                MediaTile(
                    modifier = Modifier.fillMaxWidth().height(158.dp),
                    seed = seed + 1,
                    radius = 14.dp,
                )
            }
        }
    }
}

private const val SPOILER_SCRIM = 0.88f
