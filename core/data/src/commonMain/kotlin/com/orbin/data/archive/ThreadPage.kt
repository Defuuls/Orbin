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
 *
 * Opening the page contacts nobody. A file that was not saved is a link the reader may follow,
 * never an image or player that fetches from the site on its own, and [ARCHIVE_CONTENT_POLICY]
 * has the browser enforce the same thing: an archive is often opened long after it was saved, on
 * another network, or by someone it was sent to.
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
        append("<meta http-equiv=\"Content-Security-Policy\" content=\"$ARCHIVE_CONTENT_POLICY\">\n")
        append("<meta name=\"referrer\" content=\"no-referrer\">\n")
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
    val full = saved?.full
    // Only ever a file inside the archive: a preview must not be fetched from the site.
    val preview = saved?.thumbnail ?: full?.takeIf { file.type == MediaType.IMAGE }
    val target = full ?: file.sourceUrl
    append("<figure>")
    when {
        full != null && file.type == MediaType.VIDEO -> {
            append("<video controls preload=\"metadata\"")
            if (preview != null) append(" poster=\"").appendEscaped(preview).append('"')
            append(" src=\"").appendEscaped(full).append("\"></video>")
        }
        full != null && file.type == MediaType.AUDIO ->
            append("<audio controls src=\"").appendEscaped(full).append("\"></audio>")
        preview != null -> {
            appendFileLink(target, local = full != null)
            append("<img loading=\"lazy\" src=\"")
                .appendEscaped(preview)
                .append("\" alt=\"")
                .appendEscaped(file.originalFileName)
                .append("\"></a>")
        }
        // Nothing of this file is in the archive; the caption's link is all there is to show.
        else -> Unit
    }
    val base = file.originalFileName
    val ext = file.extension.trimStart('.')
    val displayName =
        if (ext.isEmpty() || base.endsWith(".$ext", ignoreCase = true)) {
            base
        } else {
            "$base.$ext"
        }
    append("<figcaption>")
    appendFileLink(target, local = full != null)
    appendEscaped(displayName).append("</a>")
    if (file.width > 0 && file.height > 0) append(" · ").append(file.width).append('×').append(file.height)
    if (full == null) append(" · <span class=\"missing\">not saved</span>")
    append("</figcaption></figure>\n")
}

/** Opens `<a>` for a file: one in the archive, or the original on the site when it was not saved. */
private fun StringBuilder.appendFileLink(
    target: String,
    local: Boolean,
) {
    append("<a href=\"").appendEscaped(target).append('"')
    when {
        !local -> append(" rel=\"noopener noreferrer\"")
        // A file saved under the inert extension is offered as a download, never opened.
        target.endsWith(INERT_EXTENSION) -> append(" download")
    }
    append('>')
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
        InlineStyle.SPOILER -> SPOILER_OPEN_TAG to "</span></details>"
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

/**
 * Lets the page load its own stylesheet, images and media and nothing else: no script, no frames,
 * no fonts, no requests to any site. `file:` is named beside `'self'` because a page opened from
 * disk has no origin for `'self'` to match in every browser.
 */
internal const val ARCHIVE_CONTENT_POLICY =
    "default-src 'none'; img-src 'self' file:; media-src 'self' file:; style-src 'self' file:; " +
        "base-uri 'none'; form-action 'none'"

// The browser hides all descendants while closed and supplies keyboard/touch activation without script.
private const val SPOILER_OPEN_TAG =
    "<details class=\"spoiler\"><summary>Spoiler</summary><span class=\"spoiler-content\">"

/** Neko-style reader surfaces with Orbin accents; standalone archives follow browser light/dark mode. */
internal const val ARCHIVE_CSS = """:root {
  --bg: #f4f5f7; --raised: #ffffff; --placeholder: #fff4ed;
  --ink: #14161f; --muted: #696f83; --line: rgba(0, 0, 0, 0.06);
  --accent: #c2410c; --link: #0369a1; --author: #14161f; --subject: #14161f;
  --green: #2e7d32; --error: #be185d; --spoiler: #14161f; --on-spoiler: #ffffff;
  --hero-start: rgba(194, 65, 12, 0.24); --hero-end: rgba(244, 114, 182, 0.14);
  --shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  color-scheme: light dark;
}
@media (prefers-color-scheme: dark) {
  :root { --bg: #0b0d11; --raised: #141520; --placeholder: #2a1a10;
    --ink: #e8eaf0; --muted: #8a8fa8; --line: rgba(255, 255, 255, 0.07);
    --accent: #fb923c; --link: #7dd3fc; --author: #fdba74; --subject: #e8eaf0;
    --green: #34d399; --error: #f472b6; --spoiler: #e8eaf0; --on-spoiler: #141520;
    --hero-start: rgba(251, 146, 60, 0.20); --hero-end: rgba(244, 114, 182, 0.10);
    --shadow: none; }
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--ink);
  font: 15px/1.4 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
header, main, footer { max-width: 720px; margin: 0 auto; padding: 16px; }
header { width: calc(100% - 32px); max-width: 688px; margin-top: 16px; border-radius: 24px;
  border: 1px solid var(--line); background: linear-gradient(135deg, var(--hero-start), var(--hero-end)); }
h1 { font-size: 1.333em; font-weight: 800; letter-spacing: -0.4px; margin: 4px 0; overflow-wrap: anywhere; }
.board, .meta, footer { color: var(--muted); font-size: 0.8125rem; margin: 0; }
header .meta { color: var(--ink); }
.board { color: var(--accent); font-weight: 800; letter-spacing: 0.4px; }
a { color: var(--link); }
.post { background: var(--raised); border: 1px solid var(--line); border-radius: 22px;
  padding: 14px; margin: 0 0 10px; scroll-margin-top: 12px; box-shadow: var(--shadow); }
.post:target { outline: 2px solid var(--accent); }
.op { border-color: var(--accent); }
.head { font-size: 0.8125rem; color: var(--muted); display: flex; flex-wrap: wrap; gap: 6px; align-items: baseline; }
.head > * { min-width: 0; overflow-wrap: anywhere; }
.subject { color: var(--subject); font-weight: 700; }
.name { color: var(--author); font-weight: 700; }
.no { color: var(--muted); text-decoration: none; }
.files { display: flex; flex-wrap: wrap; gap: 10px; margin: 10px 0 4px; }
figure { margin: 0; max-width: 100%; }
img, video { display: block; max-width: min(100%, 360px); max-height: 360px;
  border-radius: 14px; background: var(--placeholder); }
audio { max-width: 100%; }
figcaption { font-size: 0.75rem; color: var(--muted); margin-top: 4px; overflow-wrap: anywhere; }
.missing { color: var(--error); }
blockquote { margin: 8px 0 0; overflow-wrap: anywhere; }
.green, .pink { color: var(--green); }
.quote { color: var(--link); text-decoration: underline; }
.spoiler { display: inline-block; max-width: 100%; vertical-align: baseline;
  background: var(--raised); border-radius: 14px; }
.spoiler > summary { padding: 6px 10px; border-radius: 14px; cursor: pointer; font-weight: 700;
  background: var(--spoiler); color: var(--on-spoiler); }
.spoiler > summary:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.spoiler-content { display: block; padding: 4px 8px; }
code { font-family: ui-monospace, Menlo, monospace; font-size: 0.9em; }
.replies { font-size: 0.8rem; color: var(--muted); margin: 8px 0 0; }
footer { text-align: center; padding-bottom: 32px; }
"""
