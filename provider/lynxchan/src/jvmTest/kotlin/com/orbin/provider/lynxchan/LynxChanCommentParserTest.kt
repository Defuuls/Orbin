package com.orbin.provider.lynxchan

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.InlineStyle
import com.orbin.core.model.PostNode
import org.junit.Test

class LynxChanCommentParserTest {
    private val siteUrl = "https://bbw-chan.link"

    private fun parse(
        html: String,
        board: String = "bbw",
    ) = LynxChanCommentParser.parse(html, board, siteUrl)

    @Test
    fun `plain text and entities decode`() {
        val result = parse("Tom &amp; Jerry &gt;&gt; 5 &#039;quoted&#039;")
        val text = (result.nodes.single() as PostNode.Text).text
        assertThat(text).isEqualTo("Tom & Jerry >> 5 'quoted'")
    }

    @Test
    fun `literal newline becomes line break, not br tag`() {
        val result = parse("line one\nline two")
        assertThat(result.nodes).hasSize(3)
        assertThat(result.nodes[1]).isEqualTo(PostNode.LineBreak)
        assertThat((result.nodes[0] as PostNode.Text).text).isEqualTo("line one")
        assertThat((result.nodes[2] as PostNode.Text).text).isEqualTo("line two")
    }

    @Test
    fun `crlf is also treated as a single line break`() {
        val result = parse("line one\r\nline two")
        assertThat(result.nodes).hasSize(3)
        assertThat(result.nodes[1]).isEqualTo(PostNode.LineBreak)
    }

    @Test
    fun `greentext span maps to greentext style`() {
        val result = parse("""<span class="greenText">&gt;implying</span>""")
        val styled = result.nodes.single() as PostNode.Styled
        assertThat(styled.style).isEqualTo(InlineStyle.GREENTEXT)
        assertThat((styled.children.single() as PostNode.Text).text).isEqualTo(">implying")
    }

    @Test
    fun `redText span maps to heading style`() {
        val result = parse("""<span class="redText">SRZ BIZNIZ</span>""")
        val styled = result.nodes.single() as PostNode.Styled
        assertThat(styled.style).isEqualTo(InlineStyle.HEADING)
    }

    @Test
    fun `strong em and u map to bold italic underline`() {
        assertThat((parse("<strong>b</strong>").nodes.single() as PostNode.Styled).style)
            .isEqualTo(InlineStyle.BOLD)
        assertThat((parse("<em>i</em>").nodes.single() as PostNode.Styled).style)
            .isEqualTo(InlineStyle.ITALIC)
        assertThat((parse("<u>u</u>").nodes.single() as PostNode.Styled).style)
            .isEqualTo(InlineStyle.UNDERLINE)
    }

    @Test
    fun `same-board quotelink has a null board`() {
        val html = """<a class="quoteLink" href="/bbw/res/7.html#243358">&gt;&gt;243358</a>"""
        val quote = parse(html, board = "bbw").nodes.single() as PostNode.QuoteLink
        assertThat(quote.target.value).isEqualTo(243358L)
        assertThat(quote.board).isNull()
        assertThat(quote.isDead).isFalse()
    }

    @Test
    fun `cross-board quotelink captures the board`() {
        val html = """<a class="quoteLink" href="/tits/res/50.html#50">&gt;&gt;&gt;/tits/50</a>"""
        val quote = parse(html, board = "bbw").nodes.single() as PostNode.QuoteLink
        assertThat(quote.target.value).isEqualTo(50L)
        assertThat(quote.board?.value).isEqualTo("tits")
    }

    @Test
    fun `board-only link resolves to an absolute site url`() {
        val html = """<a href="/tits/">&gt;&gt;&gt;/tits/</a>"""
        val link = parse(html).nodes.single() as PostNode.Link
        assertThat(link.url).isEqualTo("https://bbw-chan.link/tits/")
    }

    @Test
    fun `external link preserves url and text`() {
        val html = """<a href="https://example.org">example</a>"""
        val link = parse(html).nodes.single() as PostNode.Link
        assertThat(link.url).isEqualTo("https://example.org")
        assertThat((link.children.single() as PostNode.Text).text).isEqualTo("example")
    }

    @Test
    fun `unsafe link schemes are stripped from parsed links`() {
        val result = parse("""<a href="javascript:alert(1)">click</a>""")
        assertThat(result.nodes).hasSize(1)
        val text = (result.nodes.single() as PostNode.Text).text
        assertThat(text).isEqualTo("click")
    }

    @Test
    fun `quotedPosts aggregates all quote links`() {
        val html =
            """<a class="quoteLink" href="/bbw/res/7.html#1">&gt;&gt;1</a>""" +
                """<a class="quoteLink" href="/bbw/res/7.html#2">&gt;&gt;2</a>"""
        val quoted = parse(html).quotedPosts.map { it.value }
        assertThat(quoted).containsExactly(1L, 2L)
    }

    @Test
    fun `externalLinks aggregates all external links, excluding quote links`() {
        val html =
            """<a class="quoteLink" href="/bbw/res/7.html#1">&gt;&gt;1</a>""" +
                """<a href="https://example.org">one</a>""" +
                """<a href="https://example.com">two</a>"""
        val links = parse(html).externalLinks
        assertThat(links).containsExactly("https://example.org", "https://example.com")
    }
}
