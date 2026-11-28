package com.orbin.app.pulse

import com.orbin.core.model.Board
import com.orbin.core.model.Bookmark
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Post
import com.orbin.core.model.Thread
import com.orbin.core.model.activityMillis
import com.orbin.core.model.stream.PostPart
import com.orbin.core.model.stream.PostText
import com.orbin.core.model.stream.StreamIds
import com.orbin.core.model.stream.StreamText
import com.orbin.feature.home.SubscribedBoardFeed
import com.orbin.uipulse.PulseBodyLine
import com.orbin.uipulse.PulseLineUi
import com.orbin.uipulse.PulsePart

private const val HEADLINE_MAX = 140
private const val POST_MAX = 600
private const val BODY_MAX_CHARS = 2_000
private const val BODY_MAX_LINES = 40

/**
 * The subscribed feed as stream lines: every board's threads merged, most recently active first.
 *
 * A thread that cannot be addressed (see [StreamIds.thread]) has no line, and a thread that
 * appears twice keeps its first, because the stream's lines are keyed by id.
 */
internal fun feedLines(
    boards: List<SubscribedBoardFeed>,
    nowMillis: Long,
    watched: List<Bookmark> = emptyList(),
): List<PulseLineUi> {
    val lines =
        boards
            .flatMap { feed -> feed.threads.map { thread -> feed.board to thread } }
            .sortedByDescending { (_, thread) -> thread.activityMillis() }
            .mapNotNull { (board, thread) -> threadLine(board, thread, nowMillis) }
            .distinctBy { it.id }
    return withNeedsYou(lines, watched)
}

/**
 * Watched threads with new replies come first and are marked unread with their count. One the
 * feed does not list (its board is not followed) still gets a line, so nothing that needs the
 * reader is hidden by what they follow.
 */
private fun withNeedsYou(
    lines: List<PulseLineUi>,
    watched: List<Bookmark>,
): List<PulseLineUi> {
    val needing =
        watched
            .filter { it.isWatched && it.hasUnread }
            .mapNotNull { bookmark -> bookmarkId(bookmark)?.let { it to bookmark } }
            .toMap()
    if (needing.isEmpty()) return lines
    val listed = lines.mapTo(HashSet()) { it.id }
    val marked =
        lines.map { line ->
            needing[line.id]?.let { line.copy(unread = true, trailing = "+${it.unreadCount}") } ?: line
        }
    val extra =
        needing
            .filterKeys { it !in listed }
            .map { (id, bookmark) ->
                PulseLineUi(
                    id = id,
                    age = "",
                    title =
                        StreamText.headline(bookmark.title, null, bookmark.key.thread.value, HEADLINE_MAX),
                    sub = "/${bookmark.key.board.value}/",
                    trailing = "+${bookmark.unreadCount}",
                    unread = true,
                )
            }
    return (extra + marked).sortedByDescending { it.unread }
}

private fun bookmarkId(bookmark: Bookmark): String? =
    StreamIds.thread(bookmark.key.provider.value, bookmark.key.board.value, bookmark.key.thread.value)

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
): List<PulseLineUi> {
    val posts = thread.allPosts.distinctBy { it.id.value }
    val present = posts.mapTo(HashSet()) { it.id.value }
    return posts.map { post -> postLine(post, nowMillis, words, present) }
}

private fun postLine(
    post: Post,
    nowMillis: Long,
    words: PulseThreadWords,
    present: Set<Long>,
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
        number = number,
        body = bodyOf(post.comment.plainText(), present),
    )
}

/**
 * A post's text as bounded body lines. A reference to a post that is not in the thread stays
 * plain text, because there is nowhere to scroll to; a link stays plain text too.
 */
internal fun bodyOf(
    text: String,
    present: Set<Long>,
): List<PulseBodyLine> {
    if (text.isBlank()) return emptyList()
    val lines = PostText.bound(PostText.parse(text), BODY_MAX_CHARS, BODY_MAX_LINES)
    if (lines.all { it.parts.isEmpty() }) return emptyList()
    return lines.map { line ->
        PulseBodyLine(parts = line.parts.map { bodyPart(it, present) }, quote = line.isQuote)
    }
}

private fun bodyPart(
    part: PostPart,
    present: Set<Long>,
): PulsePart =
    when (part) {
        is PostPart.Text -> PulsePart.Text(part.value)
        is PostPart.Ref ->
            if (part.postNo in present) {
                PulsePart.Ref(part.raw, part.postNo)
            } else {
                PulsePart.Text(part.raw)
            }
        is PostPart.Link -> PulsePart.Text(part.url)
    }
