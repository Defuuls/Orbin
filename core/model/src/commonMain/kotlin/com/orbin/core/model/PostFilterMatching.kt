package com.orbin.core.model

/** Compiles the reader's rules once per snapshot, separately from permanent content policy. */
class UserContentFilters(
    postRules: List<PostFilterRule> = emptyList(),
    threadPatterns: List<String> = emptyList(),
) {
    private val posts = postRules.filter { it.pattern.isNotBlank() }.map { it to textMatcher(it.pattern, it.regex) }
    private val threads = threadPatterns.filter(String::isNotBlank).map { textMatcher(it, regex = true) }

    fun reasonFor(post: Post): String? =
        posts
            .firstOrNull { (rule, matches) ->
                when (rule.kind) {
                    PostFilterKind.TEXT ->
                        matches(
                            listOfNotNull(post.subject, post.comment.plainText()).joinToString("\n"),
                        )
                    PostFilterKind.NAME -> identityMatches(post.poster.name, rule, matches)
                    PostFilterKind.TRIPCODE -> identityMatches(post.poster.tripcode, rule, matches)
                    PostFilterKind.COUNTRY ->
                        identityMatches(post.poster.countryName, rule, matches) ||
                            identityMatches(post.poster.countryCode, rule, matches)
                }
            }?.first
            ?.let { "Hidden · ${it.pattern}" }

    fun hidesThread(thread: CatalogThread): Boolean {
        val text =
            listOfNotNull(
                thread.originalPost.subject,
                thread.originalPost.comment.plainText(),
            ).joinToString("\n")
        return threads.any { it(text) }
    }
}

fun Post.matchesUserPostFilter(rule: PostFilterRule): Boolean = UserContentFilters(listOf(rule)).reasonFor(this) != null

fun CatalogThread.matchesUserThreadPatterns(patterns: List<String>): Boolean =
    UserContentFilters(threadPatterns = patterns).hidesThread(this)

private fun identityMatches(
    value: String?,
    rule: PostFilterRule,
    matches: (String) -> Boolean,
): Boolean = if (rule.regex) value?.let(matches) == true else value.equals(rule.pattern.trim(), ignoreCase = true)

private fun textMatcher(
    pattern: String,
    regex: Boolean,
): (String) -> Boolean {
    val trimmed = pattern.trim()
    val compiled = if (regex) runCatching { Regex(trimmed, RegexOption.IGNORE_CASE) }.getOrNull() else null
    return { text ->
        if (compiled !=
            null
        ) {
            compiled.containsMatchIn(text)
        } else {
            text.contains(trimmed, ignoreCase = true)
        }
    }
}
