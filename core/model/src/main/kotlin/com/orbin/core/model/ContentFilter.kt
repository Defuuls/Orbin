package com.orbin.core.model

/**
 * One definition of what a hidden or muted filter token matches.
 *
 * This used to live as three private copies — two identical ones over [CatalogThread] in the feed
 * and one over [Board] on the home screen — which is how a bug survived in it: both thread copies
 * built their haystack from `originalPost.comment`, and [PostComment] is a data class, so what
 * they actually searched was its `toString()`. That string contains the node list and the field
 * names, so a token like `raw`, `nodes` or `quotelink` matched every thread in the feed. Matching
 * now uses [PostComment.raw], the comment's own text.
 *
 * A plain token matches the post's text — subject and comment — case-insensitively, as a
 * substring, which is what the setting has always done. A token may instead be prefixed to match
 * who posted it:
 *
 * - `name:moot` — the poster's name, as a substring, since names are read by people
 * - `cap:mod` — the capcode, likewise
 * - `trip:!!abc` and `id:Ab3xY` — tripcode and per-thread poster id, matched exactly (ignoring
 *   case): these are opaque identifiers, and a substring match on one would quietly catch
 *   unrelated posters that happen to share a few characters
 *
 * On top of the reader's tokens, every one of these functions also applies
 * [PermanentContentFilter], which has no setting behind it and is never skipped — not even when
 * the reader has configured no tokens at all. Folding it in here rather than at each screen is
 * deliberate: these three functions are the only way anything in the app asks "should this be
 * hidden?", so a surface cannot forget to apply it, and a surface added later gets it for free.
 */

private const val NAME_PREFIX = "name:"
private const val TRIP_PREFIX = "trip:"
private const val ID_PREFIX = "id:"
private const val CAP_PREFIX = "cap:"

/**
 * Whether this post is hidden: caught by the permanent filter, or matched by any of [tokens].
 *
 * The permanent check runs first and runs unconditionally — an empty [tokens] means the reader
 * hid nothing, not that nothing is hidden.
 */
fun Post.matchesFilterTokens(tokens: Set<String>): Boolean {
    if (isPermanentlyFiltered()) return true
    if (tokens.isEmpty()) return false
    val text = listOfNotNull(subject, comment.raw).joinToString(" ").lowercase()
    return tokens.any { token -> matches(token, text) }
}

/** Whether any of [tokens] matches the thread's opening post. */
fun CatalogThread.matchesFilterTokens(tokens: Set<String>): Boolean = originalPost.matchesFilterTokens(tokens)

/**
 * Whether this board is hidden: caught by the permanent filter, or matched by any of [tokens]
 * against its id, title or description.
 */
fun Board.matchesFilterTokens(tokens: Set<String>): Boolean {
    if (isPermanentlyFiltered()) return true
    if (tokens.isEmpty()) return false
    val haystack = listOf(id.value, title, description).joinToString(" ").lowercase()
    return tokens.any { token -> haystack.contains(token) }
}

private fun Post.matches(
    token: String,
    text: String,
): Boolean =
    when {
        token.startsWith(NAME_PREFIX) -> poster.name.containsIgnoringCase(token.removePrefix(NAME_PREFIX))
        token.startsWith(CAP_PREFIX) -> poster.capcode.containsIgnoringCase(token.removePrefix(CAP_PREFIX))
        token.startsWith(TRIP_PREFIX) -> poster.tripcode.equalsIgnoringCase(token.removePrefix(TRIP_PREFIX))
        token.startsWith(ID_PREFIX) -> poster.posterId.equalsIgnoringCase(token.removePrefix(ID_PREFIX))
        else -> text.contains(token)
    }

private fun String?.containsIgnoringCase(value: String): Boolean =
    !value.isBlank() && this != null && lowercase().contains(value)

private fun String?.equalsIgnoringCase(value: String): Boolean =
    !value.isBlank() && this != null && lowercase() == value
