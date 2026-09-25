package com.orbin.provider.vichan

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.InlineStyle
import com.orbin.core.model.PostNode
import org.junit.Test

class VichanCommentParserTest {
    @Test
    fun `plain text and entities decode`() {
        val result = VichanCommentParser.parse("Tom &amp; Jerry &gt;&gt; 5 &#039;quoted&#039;")
        val text = (result.nodes.single() as PostNode.Text).text
        assertThat(text).isEqualTo("Tom & Jerry >> 5 'quoted'")
    }

    @Test
    fun `numeric entities reject unsafe controls and decode astral characters`() {
        val result = VichanCommentParser.parse("before&#0;&#x1F;&#x1F600;after")
        val text = (result.nodes.single() as PostNode.Text).text
        assertThat(text).isEqualTo("before\uD83D\uDE00after")
    }

    @Test
    fun `numeric entities keep tab newline and carriage return`() {
        val result = VichanCommentParser.parse("a&#9;b&#10;c&#13;d")
        val text = (result.nodes.single() as PostNode.Text).text
        assertThat(text).isEqualTo("a\tb\nc\rd")
    }

    @Test
    fun `br becomes line break`() {
        val result = VichanCommentParser.parse("line one<br>line two")
        assertThat(result.nodes).hasSize(3)
        assertThat(result.nodes[1]).isEqualTo(PostNode.LineBreak)
    }

    @Test
    fun `greentext span maps to greentext style`() {
        val result = VichanCommentParser.parse("""<span class="quote">&gt;implying</span>""")
        val styled = result.nodes.single() as PostNode.Styled
        assertThat(styled.style).isEqualTo(InlineStyle.GREENTEXT)
        assertThat((styled.children.single() as PostNode.Text).text).isEqualTo(">implying")
    }

    @Test
    fun `intra-thread quotelink extracts target post id`() {
        val html = """<a href="#p123456" class="quotelink">&gt;&gt;123456</a>"""
        val quote = VichanCommentParser.parse(html).nodes.single() as PostNode.QuoteLink
        assertThat(quote.target.value).isEqualTo(123456L)
        assertThat(quote.isDead).isFalse()
        assertThat(quote.board).isNull()
    }

    @Test
    fun `cross-board quotelink captures board and target`() {
        val html = """<a href="/g/thread/100#p200" class="quotelink">&gt;&gt;&gt;/g/200</a>"""
        val quote = VichanCommentParser.parse(html).nodes.single() as PostNode.QuoteLink
        assertThat(quote.target.value).isEqualTo(200L)
        assertThat(quote.board?.value).isEqualTo("g")
    }

    @Test
    fun `deadlink is marked dead with parsed target`() {
        val html = """<span class="deadlink">&gt;&gt;999</span>"""
        val quote = VichanCommentParser.parse(html).nodes.single() as PostNode.QuoteLink
        assertThat(quote.isDead).isTrue()
        assertThat(quote.target.value).isEqualTo(999L)
    }

    @Test
    fun `external link preserves url and text`() {
        val html = """<a href="https://example.org" rel="noreferrer">example</a>"""
        val link = VichanCommentParser.parse(html).nodes.single() as PostNode.Link
        assertThat(link.url).isEqualTo("https://example.org")
        assertThat((link.children.single() as PostNode.Text).text).isEqualTo("example")
    }

    @Test
    fun `unsafe link schemes are stripped from parsed links`() {
        val result = VichanCommentParser.parse("""<a href="javascript:alert(1)">click</a>""")
        assertThat(result.nodes).hasSize(1)
        val text = (result.nodes.single() as PostNode.Text).text
        assertThat(text).isEqualTo("click")
    }

    @Test
    fun `unknown tags are transparent and keep their text`() {
        val result = VichanCommentParser.parse("a <font color=red>red</font> b")
        val joined = result.nodes.filterIsInstance<PostNode.Text>().joinToString("") { it.text }
        assertThat(joined).contains("red")
    }

    @Test
    fun `quotedPosts aggregates all quote links`() {
        val html =
            """<a href="#p1" class="quotelink">&gt;&gt;1</a> and """ +
                """<a href="#p2" class="quotelink">&gt;&gt;2</a>"""
        val quoted = VichanCommentParser.parse(html).quotedPosts.map { it.value }
        assertThat(quoted).containsExactly(1L, 2L)
    }

    @Test
    fun `externalLinks aggregates all external links, excluding quote links`() {
        val html =
            """<a href="#p1" class="quotelink">&gt;&gt;1</a> """ +
                """<a href="https://example.org">one</a> """ +
                """<a href="https://example.com">two</a>"""
        val links = VichanCommentParser.parse(html).externalLinks
        assertThat(links).containsExactly("https://example.org", "https://example.com")
    }

    @Test
    fun `externalLinks includes non-hyperlinked plain text urls`() {
        val links = VichanCommentParser.parse("check https://example.org/a and www.example.com.").externalLinks
        assertThat(links).containsExactly("https://example.org/a", "https://www.example.com")
    }
}
