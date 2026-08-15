package com.orbin.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

/**
 * The permanent filter's contract, in two halves that pull against each other: it must catch the
 * content it exists for wherever that content hides (comment, subject, poster name, filename), and
 * it must not catch ordinary words that merely contain one of its terms — because unlike a hidden
 * tag, a reader cannot delete a term that misfires.
 */
class PermanentContentFilterTest {
    private fun post(
        subject: String? = null,
        comment: String = "",
        poster: PosterInfo = PosterInfo(),
        fileNames: List<String> = emptyList(),
    ) = Post(
        id = PostId(1),
        board = BoardId("g"),
        threadId = ThreadId(1),
        isOriginalPost = true,
        subject = subject,
        comment = PostComment(raw = comment, nodes = persistentListOf()),
        poster = poster,
        attachments =
            fileNames
                .map { name ->
                    MediaAttachment(
                        id = name,
                        originalFileName = name,
                        extension = name.substringAfterLast('.', ""),
                        type = MediaType.IMAGE,
                        sourceUrl = "https://example.invalid/$name",
                        thumbnailUrl = "https://example.invalid/thumb/$name",
                    )
                }.toImmutableList(),
    )

    @Test
    fun `catches a filtered term in the comment`() {
        assertThat(PermanentContentFilter.matches("check out this gore")).isTrue()
    }

    @Test
    fun `is case insensitive`() {
        assertThat(PermanentContentFilter.matches("REKT compilation")).isTrue()
    }

    @Test
    fun `treats punctuation and underscores as word boundaries`() {
        assertThat(PermanentContentFilter.matches("gore.webm")).isTrue()
        assertThat(PermanentContentFilter.matches("[gore]")).isTrue()
        assertThat(PermanentContentFilter.matches("gore_clip")).isTrue()
    }

    /**
     * Digits are not boundaries, which is what stops the two-letter terms from firing inside the
     * alphanumeric hashes and ids that fill imageboard filenames. The cost is `gore2`.
     */
    @Test
    fun `does not treat digits as word boundaries`() {
        assertThat(PermanentContentFilter.matches("gore2")).isFalse()
        assertThat(PermanentContentFilter.matches("f8a1cp2b9.jpg")).isFalse()
    }

    /**
     * The reason this filter matches whole words rather than substrings. Every one of these is an
     * ordinary word that contains a filtered term, and every one of them would be permanently
     * unreadable under the substring matching that hidden tags use.
     */
    @Test
    fun `does not catch ordinary words that contain a filtered term`() {
        val innocent =
            listOf(
                "my parent said so",
                "the current rate",
                "torrent link",
                "different approach",
                "rendering the scene",
                "categorem",
                "a scatter plot",
                "necromancer build",
                "concatenate the strings",
            )
        innocent.forEach { text ->
            assertThat(PermanentContentFilter.matches(text)).isFalse()
        }
    }

    @Test
    fun `matches a multi-word term however it is spaced`() {
        assertThat(PermanentContentFilter.matches("self harm")).isTrue()
        assertThat(PermanentContentFilter.matches("self-harm")).isTrue()
        assertThat(PermanentContentFilter.matches("selfharm")).isTrue()
        assertThat(PermanentContentFilter.matches("self_harm")).isTrue()
    }

    @Test
    fun `ignores blank and missing text`() {
        assertThat(PermanentContentFilter.matches(null)).isFalse()
        assertThat(PermanentContentFilter.matches("")).isFalse()
        assertThat(PermanentContentFilter.matches("   ")).isFalse()
    }

    @Test
    fun `catches a post by its subject`() {
        assertThat(post(subject = "gore thread").isPermanentlyFiltered()).isTrue()
    }

    @Test
    fun `catches a post by its poster name`() {
        assertThat(post(poster = PosterInfo(name = "gorelover")).isPermanentlyFiltered()).isFalse()
        assertThat(post(poster = PosterInfo(name = "gore lover")).isPermanentlyFiltered()).isTrue()
    }

    /** Shock content routinely has an innocuous comment and a descriptive filename. */
    @Test
    fun `catches a post whose comment is clean but whose filename is not`() {
        val hidden = post(comment = "look at this", fileNames = listOf("rekt-compilation.webm"))
        assertThat(hidden.isPermanentlyFiltered()).isTrue()
    }

    @Test
    fun `leaves an ordinary post alone`() {
        val ordinary =
            post(
                subject = "Parent thread",
                comment = "current torrent prices",
                fileNames = listOf("cat.jpg"),
            )
        assertThat(ordinary.isPermanentlyFiltered()).isFalse()
    }

    /**
     * `rent` was requested alongside gore and is filtered as a standalone word, which does mean the
     * housing sense of the word goes with it. Pinned here so the trade-off is visible rather than
     * discovered: whole-word matching keeps `parent`/`current`/`torrent` readable (asserted above),
     * but a post that says "rent" on its own is filtered.
     */
    @Test
    fun `filters the standalone word rent, including its everyday sense`() {
        assertThat(PermanentContentFilter.matches("rent is too high")).isTrue()
    }

    @Test
    fun `catches a board by id, title or description`() {
        assertThat(board(id = "gore").isPermanentlyFiltered()).isTrue()
        assertThat(board(title = "Guro").isPermanentlyFiltered()).isTrue()
        assertThat(board(description = "Photos of beheading videos").isPermanentlyFiltered()).isTrue()
        assertThat(board(id = "g", title = "Technology").isPermanentlyFiltered()).isFalse()
    }

    /**
     * The guarantee the whole change rests on: an empty token set means the reader hid nothing, not
     * that nothing is hidden. Every screen calls [matchesFilterTokens], so this is what makes the
     * filter unavoidable rather than something each screen has to remember.
     */
    @Test
    fun `applies through matchesFilterTokens even with no reader tokens`() {
        assertThat(post(comment = "gore").matchesFilterTokens(emptySet())).isTrue()
        assertThat(board(id = "gore").matchesFilterTokens(emptySet())).isTrue()
        assertThat(post(comment = "hello").matchesFilterTokens(emptySet())).isFalse()
    }

    private fun board(
        id: String = "b",
        title: String = "Random",
        description: String = "",
    ) = Board(id = BoardId(id), title = title, description = description)
}
