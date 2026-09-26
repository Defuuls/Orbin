package com.orbin.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

/** The catalog search Android and iOS both run over the boards the reader follows. */
class SearchMatchingTest {
    @Test
    fun textMatchesTheSubjectOrTheCommentIgnoringCase() {
        val thread = thread(subject = "Rust thread", comment = "Borrow checker tips")

        assertThat(thread.matchesSearch(query("rust"))).isTrue()
        assertThat(thread.matchesSearch(query("  BORROW "))).isTrue()
        assertThat(thread.matchesSearch(query("golang"))).isFalse()
    }

    @Test
    fun aBlankQueryMatchesEverything() {
        assertThat(thread().matchesSearch(query("   "))).isTrue()
    }

    @Test
    fun filtersNarrowTextMatches() {
        val textOnly = thread(comment = "see https://example.com")
        val withVideo = thread(comment = "see", files = listOf(MediaType.VIDEO))

        assertThat(textOnly.matchesSearch(query("see", SearchFilters(mediaOnly = true)))).isFalse()
        assertThat(withVideo.matchesSearch(query("see", SearchFilters(mediaOnly = true)))).isTrue()
        assertThat(textOnly.matchesSearch(query("see", contentTypes(SearchContentType.URL)))).isTrue()
        assertThat(textOnly.matchesSearch(query("see", contentTypes(SearchContentType.VIDEO)))).isFalse()
        assertThat(withVideo.matchesSearch(query("see", contentTypes(SearchContentType.VIDEO)))).isTrue()
    }

    @Test
    fun aResultNamesTheThreadOrItsBoardAndKeepsTheSnippetShort() {
        val untitled = thread(subject = null, comment = "x".repeat(500), files = listOf(MediaType.IMAGE))

        val result = untitled.toSearchResult()

        assertThat(result.title).isEqualTo("/g/")
        assertThat(result.snippet).hasLength(160)
        assertThat(result.thumbnailUrl).isEqualTo("https://i.example/0s.jpg")
        assertThat(result.matchedPost).isEqualTo(PostId(7))
    }

    private fun query(
        text: String,
        filters: SearchFilters = SearchFilters(),
    ) = SearchQuery(ProviderId("p"), text, SearchScope.BOARD_CATALOG, BoardId("g"), filters)

    private fun contentTypes(vararg types: SearchContentType) = SearchFilters(contentTypes = types.toSet())

    private fun thread(
        subject: String? = "Hi",
        comment: String = "",
        files: List<MediaType> = emptyList(),
    ) = CatalogThread(
        key = ThreadKey(ProviderId("p"), BoardId("g"), ThreadId(7)),
        originalPost =
            Post(
                id = PostId(7),
                board = BoardId("g"),
                threadId = ThreadId(7),
                isOriginalPost = true,
                subject = subject,
                comment = PostComment(raw = comment, nodes = persistentListOf()),
                attachments =
                    persistentListOf(
                        *files
                            .mapIndexed { index, type ->
                                MediaAttachment(
                                    id = "$index",
                                    originalFileName = "f$index",
                                    extension = "x",
                                    type = type,
                                    sourceUrl = "https://i.example/$index",
                                    thumbnailUrl = "https://i.example/${index}s.jpg",
                                )
                            }.toTypedArray(),
                    ),
            ),
        stats = ThreadStats(),
    )
}
