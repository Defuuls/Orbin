package com.orbin.core.model

/**
 * The filter the reader cannot turn off.
 *
 * Hidden tags are a preference: an empty setting means nothing is hidden, and every call site
 * honours that by skipping the filter entirely. This filter is not a preference. It is a fixed
 * list of shock-content terms — gore and the categories that travel with it — that is applied on
 * every surface that shows post text, board metadata or a filename, whether or not the reader has
 * configured anything. There is deliberately no setting, no toggle and no override: the whole
 * point is that it survives a settings reset, a restored backup, and a fresh install.
 *
 * ## How broad this is
 *
 * Deliberately very. Alongside the unambiguous terms (`guro`, `beheading`, `bestiality`) the list
 * carries bare words like `death`, `gun`, `murder`, `violence`, `hanging` and `suicide`, which
 * occur constantly in threads that have nothing to do with shock content. Filtering them removes a
 * great deal of ordinary discussion — "death metal", "gun control", "hanging out", suicide
 * prevention — and that is the intended trade: the filter was asked to err heavily towards
 * removing too much. [PermanentContentFilterTest] pins the main false positives so the cost stays
 * visible in review rather than being discovered from a bug report.
 *
 * ## Why word boundaries, not substrings
 *
 * Hidden tags match as substrings, because a reader who types `webm` means "anything with webm in
 * it" and can fix a bad token by deleting it. Nobody can delete these, so a false positive here is
 * permanent — and short terms are exactly where substring matching goes wrong: `gore` is inside
 * `categorem`, `gun` is inside `shotgun`, and `rent` is inside `parent`, `current` and `torrent`.
 * Matching whole words only is the one thing keeping an already-blunt list from being unusable.
 * `_` and punctuation count as boundaries, so `gore_video`, `[gore]` and `gore.webm` are all
 * caught while `parent` is not.
 *
 * Digits deliberately do **not** count as boundaries, so `gore2` is not caught. That costs a
 * little recall on filenames, and buys the thing that matters more: the two-letter terms in the
 * list cannot fire inside the alphanumeric hashes and ids that fill imageboard filenames and
 * tripcodes, where `…a1cp2b…` would otherwise read as a match.
 *
 * ## What is checked
 *
 * Post subject and comment text, the poster's name, and every attachment's original filename —
 * shock content is routinely posted with an innocuous comment and a descriptive filename, so text
 * alone would miss it. For boards, the id, title and description, so a board dedicated to this
 * material never appears in a list.
 *
 * This is a keyword filter over the text an engine gives us. It cannot inspect pixels, so it
 * reduces exposure rather than eliminating it; it is a floor, not a guarantee.
 */
object PermanentContentFilter {
    /**
     * Terms that are always filtered, lowercase and matched as whole words.
     *
     * Grouped by what they describe rather than alphabetically, so the list stays reviewable.
     * A space in a term means "separator or nothing", so the single entry `self harm` covers
     * `self harm`, `self-harm`, `self_harm` and `selfharm`.
     */
    val terms: Set<String> =
        setOf(
            // Gore and shock imagery, plus the boards and sites it is sourced from.
            "gore",
            "guro",
            "gurochan",
            "bestgore",
            "goretube",
            "liveleak",
            "rekt",
            "goro",
            "wpd",
            "watch people die",
            "shock site",
            // Broad violence terms. These are the widest entries in the list by a long way:
            // unlike `guro` or `beheading`, they are ordinary English words that appear constantly
            // in threads that have nothing to do with shock content — "death metal", "gun
            // control", "murder mystery", "violence in games". They are here because the filter
            // was asked to err on the side of removing too much, and whole-word matching is doing
            // real work behind them (it is what keeps `shotgun` and `deathmatch` readable). If the
            // filter is later reported as too aggressive, this is the group to look at first.
            "death",
            "murder",
            "violence",
            "gun",
            "hanging",
            // Explicit violence against people.
            "beheading",
            "beheaded",
            "decapitation",
            "decapitated",
            "dismemberment",
            "dismembered",
            "mutilation",
            "mutilated",
            "disembowelment",
            "disembowled",
            "disemboweled",
            "snuff",
            "necro",
            "necrophilia",
            "ryona",
            // Self-harm. `suicide` covers the prevention and support discussions too, which is a
            // real cost of filtering the bare word rather than only phrases like `suicide pact`.
            "suicide",
            "self harm",
            // Sexual content involving minors or animals. These are illegal in most
            // jurisdictions, so they are filtered rather than merely de-emphasised.
            "cp",
            "child porn",
            "child pornography",
            "cheese pizza",
            "pedo",
            "pedophile",
            "jailbait",
            "lolicon",
            "shotacon",
            "bestiality",
            "zoophilia",
            // Scat and related shock fetish content.
            "scat",
            "coprophilia",
            // Requested explicitly alongside gore. Note this is the housing/tenancy word too, so
            // whole-word matching is what keeps it from swallowing `parent`, `current` and
            // `torrent`; if it was meant as a misspelling of `rekt` (already listed above), this
            // single line is safe to delete.
            "rent",
        )

    /** A single letter or digit — what counts as being "inside a word". */
    private const val WORD_CHAR = "[\\p{L}\\p{N}]"

    /** A single character that is neither a letter nor a digit, i.e. a word boundary. */
    private const val SEPARATOR = "[^\\p{L}\\p{N}]"

    /**
     * One pattern for the whole list. Building it once matters: this runs per post, per reply and
     * per catalog cell on every settings change and every page load.
     *
     * Each term is escaped in case one ever contains regex punctuation, and the space in a
     * multi-word term becomes "any run of separators, or none" so `self harm` also matches
     * `self-harm` and `selfharm`. The alternation is wrapped in lookarounds rather than `\b`
     * because `\b` would treat `_` as a word character and miss `gore_video`.
     */
    private val pattern: Regex =
        Regex(
            terms
                .sortedByDescending { it.length }
                .joinToString(
                    separator = "|",
                    prefix = "(?<!$WORD_CHAR)(?:",
                    postfix = ")(?!$WORD_CHAR)",
                ) { term ->
                    term.split(' ').joinToString("$SEPARATOR*") { word -> Regex.escape(word) }
                },
            RegexOption.IGNORE_CASE,
        )

    /** Whether any filtered term appears as a whole word in [text]. */
    fun matches(text: String?): Boolean = !text.isNullOrBlank() && pattern.containsMatchIn(text)

    /** Whether any filtered term appears as a whole word in any of [texts]. */
    fun matchesAny(texts: Iterable<String?>): Boolean = texts.any { matches(it) }
}

/**
 * Whether this post is caught by the permanent filter — its text, its poster name, or the name of
 * any file attached to it.
 */
fun Post.isPermanentlyFiltered(): Boolean =
    PermanentContentFilter.matchesAny(
        listOf(subject, comment.raw, poster.name) +
            attachments.map { it.originalFileName },
    )

/** Whether this attachment's filename is caught by the permanent filter. */
fun MediaAttachment.isPermanentlyFiltered(): Boolean = PermanentContentFilter.matches(originalFileName)

/**
 * Whether this thread is caught by the permanent filter through its opening post. Replies are
 * filtered individually rather than condemning the whole thread, the same way hidden tags work.
 */
fun CatalogThread.isPermanentlyFiltered(): Boolean = originalPost.isPermanentlyFiltered()

/** Whether this thread's opening post is caught by the permanent filter. */
fun Thread.isPermanentlyFiltered(): Boolean = originalPost.isPermanentlyFiltered()

/**
 * Whether this search hit is caught by the permanent filter. A hit carries only the text it was
 * reduced to, so this checks the title and snippet; callers that still hold the underlying
 * [CatalogThread] should filter that instead, since it also carries filenames.
 */
fun SearchResult.isPermanentlyFiltered(): Boolean = PermanentContentFilter.matchesAny(listOf(title, snippet))

/** Whether this history entry is caught by the permanent filter through the title it recorded. */
fun HistoryEntry.isPermanentlyFiltered(): Boolean = PermanentContentFilter.matches(title)

/** Whether this bookmark is caught by the permanent filter through the title it recorded. */
fun Bookmark.isPermanentlyFiltered(): Boolean = PermanentContentFilter.matches(title)

/** Whether this board is caught by the permanent filter through its id, title or description. */
fun Board.isPermanentlyFiltered(): Boolean = PermanentContentFilter.matchesAny(listOf(id.value, title, description))
