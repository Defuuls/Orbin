package com.orbin.core.ui.post

import com.orbin.core.model.PostNode

/**
 * The host a link really opens, when its visible text names a different one; otherwise null.
 *
 * Board HTML lets a poster write `<a href="https://evil.example">paypal.com</a>`, and the anchor
 * text is all the reader sees. When the text itself reads as a web address or a domain, and the
 * link goes somewhere else, the renderer shows the real host next to it so the destination is
 * never hidden. Text that does not look like an address ("source", "here") is left alone: it makes
 * no claim about where it goes.
 *
 * A link to a subdomain of the host the text names (text `example.com`, link `docs.example.com`)
 * is not flagged, and neither is the `www.` prefix on either side. Internationalised hosts are
 * compared as written, so text in Unicode that links to a punycode host shows the punycode — which
 * is the point for look-alike domains.
 */
internal fun misleadingLinkHost(
    text: String,
    url: String,
): String? {
    val claimed = claimedHost(text) ?: return null
    val actual = urlHost(url) ?: return null
    val claimedKey = claimed.withoutWww()
    val actualKey = actual.withoutWww()
    return if (actualKey == claimedKey || actualKey.endsWith(".$claimedKey")) null else actual
}

/** The link's visible text, as the reader sees it. */
internal fun PostNode.Link.visibleText(): String = buildString { appendVisible(children) }

private fun StringBuilder.appendVisible(nodes: List<PostNode>) {
    nodes.forEach { node ->
        when (node) {
            is PostNode.Text -> append(node.text)
            is PostNode.Styled -> appendVisible(node.children)
            is PostNode.Link -> appendVisible(node.children)
            PostNode.LineBreak -> append(' ')
            is PostNode.QuoteLink -> Unit
        }
    }
}

/**
 * The host named by [text] when the whole text reads as an address: an optional scheme, a dotted
 * host ending in an alphabetic TLD (or an IPv4 address), then an optional port and path.
 */
private fun claimedHost(text: String): String? =
    addressLikeText
        .matchEntire(text.trim())
        ?.groupValues
        ?.get(1)
        ?.lowercase()
        ?.trimEnd('.')

private fun urlHost(url: String): String? {
    val afterScheme = url.trim().replaceFirst(schemePrefix, "")
    val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    val host =
        authority
            .substringAfterLast('@')
            .let { if (it.startsWith("[")) it.substringBefore(']') + "]" else it.substringBefore(':') }
            .lowercase()
            .trimEnd('.')
    return host.ifEmpty { null }
}

private fun String.withoutWww(): String = removePrefix("www.")

private val schemePrefix = Regex("""^[A-Za-z][A-Za-z0-9+.\-]*://""")

private val addressLikeText =
    Regex(
        """(?i)^(?:[a-z][a-z0-9+.\-]*://)?""" +
            """((?:[\p{L}\p{N}](?:[\p{L}\p{N}\-]*[\p{L}\p{N}])?\.)+\p{L}{2,}|\d{1,3}(?:\.\d{1,3}){3})\.?""" +
            """(?::\d{1,5})?(?:[/?#]\S*)?$""",
    )
