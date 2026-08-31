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
 * Two lists. [terms] is always on: unambiguous shock and illegal categories. [harshTerms] is the
 * everyday English (`death`, `gun`, `suicide`, `rent`) and is opt-in through Settings. The default
 * therefore leaves ordinary discussion readable. [PermanentContentFilterTest] pins both lists.
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
            // Self-harm phrases. The bare word `suicide` lives in [harshTerms].
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
        )

    val harshTerms: Set<String> =
        setOf(
            "death",
            "murder",
            "violence",
            "gun",
            "hanging",
            "suicide",
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
     * `self-harm` and `selfharm`. The alternation is wrapped in lookarounds rather than `\\b`
     * because `\\b` would treat `_` as a word character and miss `gore_video`.
     */
    private val alwaysPattern: Regex = compile(terms)
    private val harshPattern: Regex = compile(terms + harshTerms)

    fun matches(text: String?): Boolean = matches(text, includeHarsh = false)

    fun matches(
        text: String?,
        includeHarsh: Boolean,
    ): Boolean {
        if (text.isNullOrBlank()) return false
        return (if (includeHarsh) harshPattern else alwaysPattern).containsMatchIn(text)
    }

    fun matchesAny(
        texts: Iterable<String?>,
        includeHarsh: Boolean = false,
    ): Boolean = texts.any { matches(it, includeHarsh) }

    private fun compile(source: Set<String>): Regex =
        Regex(
            source
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
}

/**
 * Whether this post is caught by the permanent filter — its text, its poster name, or the name of
 * any file attached to it.
 */
fun Post.isPermanentlyFiltered(includeHarsh: Boolean = false): Boolean =
    PermanentContentFilter.matchesAny(
        listOf(subject, comment.raw, poster.name) +
            attachments.map { it.originalFileName },
        includeHarsh,
    )

/** Whether this attachment's filename is caught by the permanent filter. */
fun MediaAttachment.isPermanentlyFiltered(includeHarsh: Boolean = false): Boolean =
    PermanentContentFilter.matches(originalFileName, includeHarsh)

/**
 * Whether this thread is caught by the permanent filter through its opening post. Replies are
 * filtered individually rather than condemning the whole thread, the same way hidden tags work.
 */
fun CatalogThread.isPermanentlyFiltered(includeHarsh: Boolean = false): Boolean =
    originalPost.isPermanentlyFiltered(includeHarsh)

/** Whether this thread's opening post is caught by the permanent filter. */
fun Thread.isPermanentlyFiltered(includeHarsh: Boolean = false): Boolean =
    originalPost.isPermanentlyFiltered(includeHarsh)

/**
 * Whether this search hit is caught by the permanent filter. A hit carries only the text it was
 * reduced to, so this checks the title and snippet; callers that still hold the underlying
 * [CatalogThread] should filter that instead, since it also carries filenames.
 */
fun SearchResult.isPermanentlyFiltered(includeHarsh: Boolean = false): Boolean =
    PermanentContentFilter.matchesAny(listOf(title, snippet), includeHarsh)

/** Whether this history entry is caught by the permanent filter through the title it recorded. */
fun HistoryEntry.isPermanentlyFiltered(includeHarsh: Boolean = false): Boolean =
    PermanentContentFilter.matches(title, includeHarsh)

/** Whether this bookmark is caught by the permanent filter through the title it recorded. */
fun Bookmark.isPermanentlyFiltered(includeHarsh: Boolean = false): Boolean =
    PermanentContentFilter.matches(title, includeHarsh)

/** Whether this board is caught by the permanent filter through its id, title or description. */
fun Board.isPermanentlyFiltered(includeHarsh: Boolean = false): Boolean =
    PermanentContentFilter.matchesAny(listOf(id.value, title, description), includeHarsh)
