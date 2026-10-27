package com.orbin.data.archive

import com.orbin.core.model.InlineStyle
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostNode
import com.orbin.core.model.Thread
import kotlin.time.Instant

/** Where an attachment's files sit inside the archive; null where the file couldn't be saved. */
internal data class SavedFiles(
    val full: String?,
    val thumbnail: String?,
)

/**
 * The archive's `index.html`: every post in reading order, quotes linking to the post they quote,
 * replies listed under each post, and each file shown from the archive itself (or, if it couldn't
 * be saved, linked on the site). Nothing runs: the page is plain HTML and [ARCHIVE_CSS].
 */
internal fun threadPage(
    thread: Thread,
    files: Map<String, SavedFiles>,
    sourceUrl: String?,
    savedAtMillis: Long,
): String =
    buildString {
        val title =
            thread.subject?.takeIf { it.isNotBlank() } ?: thread.originalPost.comment
                .plainText()
                .take(TITLE_LENGTH)
        val board = thread.key.board.value
        append("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        append(
            "<title>",
        ).appendEscaped("/$board/ – ${title.ifBlank { "Thread ${thread.key.thread.value}" }}").append("</title>\n")
        append("<link rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>\n<header>\n")
        append(
            "<p class=\"board\">/",
        ).appendEscaped(board).append("/ · ").appendEscaped(thread.key.provider.value).append("</p>\n")
        append("<h1>").appendEscaped(title.ifBlank { "Thread ${thread.key.thread.value}" }).append("</h1>\n")
        append("<p class=\"meta\">")
            .append(thread.allPosts.size)
            .append(" posts · saved ")
            .appendEscaped(Instant.fromEpochMilliseconds(savedAtMillis).toString())
        if (sourceUrl != null) {
            append(
                " · <a href=\"",
            ).appendEscaped(sourceUrl).append("\" rel=\"noopener noreferrer\">original thread</a>")
        }
        append("</p>\n</header>\n<main>\n")
        thread.allPosts.forEach { post -> appendPost(post, files) }
        append("</main>\n<footer>Saved with Orbin</footer>\n</body>\n</html>\n")
    }

private fun StringBuilder.appendPost(
    post: Post,
    files: Map<String, SavedFiles>,
) {
    append("<article id=\"p").append(post.id.value).append("\" class=\"post")
    if (post.isOriginalPost) append(" op")
    append("\">\n<div class=\"head\">")
    post.subject
        ?.takeIf {
            it.isNotBlank()
        }?.let { append("<span class=\"subject\">").appendEscaped(it).append("</span> ") }
    append("<span class=\"name\">").appendEscaped(post.poster.name ?: "Anonymous").append("</span>")
    post.poster.tripcode?.let { append(" <span class=\"trip\">").appendEscaped(it).append("</span>") }
    post.poster.capcode?.let { append(" <span class=\"capcode\">").appendEscaped(it).append("</span>") }
    post.poster.posterId?.let { append(" <span class=\"pid\">ID: ").appendEscaped(it).append("</span>") }
    if (post.createdAtMillis > 0) {
        append(
            " <time>",
        ).appendEscaped(Instant.fromEpochMilliseconds(post.createdAtMillis).toString()).append("</time>")
    }
    append(" <a class=\"no\" href=\"#p")
        .append(post.id.value)
        .append("\">No. ")
        .append(post.id.value)
        .append("</a>")
    append("</div>\n")
    if (post.attachments.isNotEmpty()) {
        append("<div class=\"files\">\n")
        post.attachments.forEach { appendFile(it, files[it.id]) }
        append("</div>\n")
    }
    append("<blockquote>")
    appendNodes(post.comment.nodes)
    append("</blockquote>\n")
    if (post.backlinks.isNotEmpty()) {
        append("<p class=\"replies\">Replies:")
        post.backlinks.forEach {
            append(" <a href=\"#p")
                .append(it.value)
                .append("\">&gt;&gt;")
                .append(it.value)
                .append("</a>")
        }
        append("</p>\n")
    }
    append("</article>\n")
}

private fun StringBuilder.appendFile(
    file: MediaAttachment,
    saved: SavedFiles?,
) {
    val full = saved?.full ?: file.sourceUrl
    val thumbnail = saved?.thumbnail ?: saved?.full?.takeIf { file.type == MediaType.IMAGE } ?: file.thumbnailUrl
    append("<figure>")
    when (file.type) {
        MediaType.VIDEO ->
            append("<video controls preload=\"metadata\" poster=\"")
                .appendEscaped(thumbnail)
                .append("\" src=\"")
                .appendEscaped(full)
                .append("\"></video>")
        MediaType.AUDIO -> append("<audio controls src=\"").appendEscaped(full).append("\"></audio>")
        else ->
            append("<a href=\"")
                .appendEscaped(full)
                .append("\"><img loading=\"lazy\" src=\"")
                .appendEscaped(thumbnail)
                .append("\" alt=\"")
                .appendEscaped(file.originalFileName)
                .append("\"></a>")
    }
    val base = file.originalFileName
    val ext = file.extension.trimStart('.')
    val displayName =
        if (ext.isEmpty() || base.endsWith(".$ext", ignoreCase = true)) {
            base
        } else {
            "$base.$ext"
        }
    append("<figcaption><a href=\"")
        .appendEscaped(full)
        .append("\">")
        .appendEscaped(displayName)
        .append("</a>")
    if (file.width > 0 && file.height > 0) append(" · ").append(file.width).append('×').append(file.height)
    if (saved?.full == null) append(" · <span class=\"missing\">not saved</span>")
    append("</figcaption></figure>\n")
}

private fun StringBuilder.appendNodes(nodes: List<PostNode>) {
    nodes.forEach { node ->
        when (node) {
            is PostNode.Text -> appendEscaped(node.text)
            PostNode.LineBreak -> append("<br>")
            is PostNode.Styled -> {
                val (open, close) = tagsFor(node.style)
                append(open)
                appendNodes(node.children)
                append(close)
            }
            is PostNode.QuoteLink -> appendQuote(node)
            is PostNode.Link -> appendLink(node)
        }
    }
}

private fun StringBuilder.appendQuote(node: PostNode.QuoteLink) {
    val label =
        buildString {
            append("&gt;&gt;")
            node.board?.let { append("&gt;/").appendEscaped(it.value).append('/') }
            append(node.target.value)
            if (node.isOp) append(" (OP)")
        }
    if (node.board == null) {
        append("<a class=\"quote\" href=\"#p")
            .append(node.target.value)
            .append("\">")
            .append(label)
            .append("</a>")
    } else {
        append("<span class=\"quote\">").append(label).append("</span>")
    }
}

/** A link only for http(s); anything else (javascript:, data:) is kept as text. */
private fun StringBuilder.appendLink(node: PostNode.Link) {
    val safe = node.url.startsWith("https://", true) || node.url.startsWith("http://", true)
    if (safe) append("<a href=\"").appendEscaped(node.url).append("\" rel=\"noopener noreferrer\">")
    if (node.children.isEmpty()) appendEscaped(node.url) else appendNodes(node.children)
    if (safe) append("</a>")
}

private fun tagsFor(style: InlineStyle): Pair<String, String> =
    when (style) {
        InlineStyle.GREENTEXT -> "<span class=\"green\">" to "</span>"
        InlineStyle.QUOTE_TEXT -> "<span class=\"pink\">" to "</span>"
        InlineStyle.SPOILER -> "<span class=\"spoiler\">" to "</span>"
        InlineStyle.BOLD -> "<b>" to "</b>"
        InlineStyle.ITALIC -> "<i>" to "</i>"
        InlineStyle.UNDERLINE -> "<u>" to "</u>"
        InlineStyle.STRIKETHROUGH -> "<s>" to "</s>"
        InlineStyle.CODE -> "<code>" to "</code>"
        InlineStyle.HEADING -> "<strong class=\"heading\">" to "</strong>"
    }

internal fun StringBuilder.appendEscaped(text: String): StringBuilder {
    text.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }
    return this
}

private const val TITLE_LENGTH = 80

/** The archive's stylesheet: Orbin's forest green dark theme, or light where the reader prefers it. */
internal const val ARCHIVE_CSS = """:root {
  --bg: #f7fbf7; --raised: #ffffff; --ink: #191c19; --muted: #404941; --line: #c0c9bf;
  --accent: #276a3c; --green: #2f7d32; --pink: #b0306a;
  color-scheme: light dark;
}
@media (prefers-color-scheme: dark) {
  :root { --bg: #101a12; --raised: #1c271e; --ink: #e0e8e0; --muted: #c0c9bf; --line: #404941;
    --accent: #a3d4af; --green: #8bc98d; --pink: #f08cb8; }
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--ink);
  font: 15px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
header, main, footer { max-width: 820px; margin: 0 auto; padding: 16px; }
h1 { font-size: 1.4rem; margin: 4px 0; overflow-wrap: anywhere; }
.board, .meta, footer { color: var(--muted); font-size: 0.85rem; margin: 0; }
a { color: var(--accent); }
.post { background: var(--raised); border: 1px solid var(--line); border-radius: 12px;
  padding: 12px 14px; margin: 0 0 10px; scroll-margin-top: 12px; }
.post:target { outline: 2px solid var(--accent); }
.op { border-color: var(--accent); }
.head { font-size: 0.85rem; color: var(--muted); display: flex; flex-wrap: wrap; gap: 6px; align-items: baseline; }
.subject { color: var(--ink); font-weight: 600; }
.name { color: var(--ink); font-weight: 600; }
.no { color: var(--muted); text-decoration: none; }
.files { display: flex; flex-wrap: wrap; gap: 10px; margin: 10px 0 4px; }
figure { margin: 0; max-width: 100%; }
img, video { display: block; max-width: min(100%, 360px); max-height: 360px; border-radius: 8px; background: var(--line); }
audio { max-width: 100%; }
figcaption { font-size: 0.75rem; color: var(--muted); margin-top: 4px; overflow-wrap: anywhere; }
.missing { color: var(--pink); }
blockquote { margin: 8px 0 0; overflow-wrap: anywhere; }
.green { color: var(--green); }
.pink { color: var(--pink); }
.quote { color: var(--accent); }
.spoiler { background: var(--ink); color: var(--ink); border-radius: 3px; }
.spoiler:hover, .spoiler:focus { color: var(--bg); }
code { font-family: ui-monospace, Menlo, monospace; font-size: 0.9em; }
.replies { font-size: 0.8rem; color: var(--muted); margin: 8px 0 0; }
footer { text-align: center; padding-bottom: 32px; }
"""
