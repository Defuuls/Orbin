package com.orbin.app.pulse

import com.orbin.core.model.Board
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Post
import com.orbin.core.model.Thread
import com.orbin.core.model.activityMillis
import com.orbin.core.model.stream.StreamIds
import com.orbin.core.model.stream.StreamText
import com.orbin.feature.home.SubscribedBoardFeed
import com.orbin.uipulse.PulseLineUi

private const val HEADLINE_MAX = 140
private const val POST_MAX = 600

/**
 * The subscribed feed as stream lines: every board's threads merged, most recently active first.
 *
 * A thread that cannot be addressed (see [StreamIds.thread]) has no line, and a thread that
 * appears twice keeps its first, because the stream's lines are keyed by id.
 */
internal fun feedLines(
    boards: List<SubscribedBoardFeed>,
    nowMillis: Long,
): List<PulseLineUi> =
    boards
        .flatMap { feed -> feed.threads.map { thread -> feed.board to thread } }
        .sortedByDescending { (_, thread) -> thread.activityMillis() }
        .mapNotNull { (board, thread) -> threadLine(board, thread, nowMillis) }
        .distinctBy { it.id }

private fun threadLine(
    board: Board,
    thread: CatalogThread,
    nowMillis: Long,
): PulseLineUi? {
    val key = thread.key
    val id = StreamIds.thread(key.provider.value, key.board.value, key.thread.value) ?: return null
    return PulseLineUi(
        id = id,
        age = StreamText.age(nowMillis, thread.activityMillis()),
        title =
            StreamText.headline(
                subject = thread.originalPost.subject,
                body = thread.originalPost.comment.plainText(),
                fallbackNo = key.thread.value,
                max = HEADLINE_MAX,
            ),
        sub = listOf("/${key.board.value}/", board.title).filter { it.isNotBlank() }.joinToString(" · "),
        trailing = thread.stats.replyCount.toString(),
    )
}

/** The few words a post line shows, supplied by the host so this file needs no resources. */
internal class PulseThreadWords(
    val anonymous: String,
    val media: (Int) -> String,
)

/**
 * A thread as stream lines, opening post first, in reading order. A post number seen twice keeps
 * its first line, because the stream's lines are keyed by id.
 */
internal fun threadLines(
    thread: Thread,
    nowMillis: Long,
    words: PulseThreadWords,
): List<PulseLineUi> =
    thread.allPosts
        .distinctBy { it.id.value }
        .map { post -> postLine(post, nowMillis, words) }

private fun postLine(
    post: Post,
    nowMillis: Long,
    words: PulseThreadWords,
): PulseLineUi {
    val number = post.id.value
    val body = post.comment.plainText().takeIf { it.isNotBlank() } ?: post.subject
    val posterName = post.poster.name?.takeIf { it.isNotBlank() } ?: words.anonymous
    val mediaCount = post.attachments.size
    val details =
        listOfNotNull(
            "No.$number",
            posterName,
            if (mediaCount > 0) words.media(mediaCount) else null,
        )
    return PulseLineUi(
        id = StreamIds.post(number),
        age = StreamText.age(nowMillis, post.createdAtMillis),
        title = StreamText.headline(subject = body, body = null, fallbackNo = number, max = POST_MAX),
        sub = details.joinToString(" · "),
        trailing = if (post.backlinks.isNotEmpty()) "+${post.backlinks.size}" else "",
    )
}
