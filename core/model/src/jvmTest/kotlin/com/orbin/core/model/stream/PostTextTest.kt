package com.orbin.core.model.stream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PostTextTest {
    private fun types(line: PostLine) = line.parts.map { it::class.simpleName }

    @Test
    fun refsGreentextAndPlainTextBecomeSegments() {
        val (a, b, c, d) = PostText.parse(">>12 agreed\n>quoted line\n>>7>>8\n<b>x</b> >>99999999999999")
        assertThat(a).isEqualTo(PostLine(false, listOf(PostPart.Ref(">>12", 12), PostPart.Text(" agreed"))))
        assertThat(b).isEqualTo(PostLine(true, listOf(PostPart.Text("quoted line"))))
        assertThat(c.parts.map { (it as PostPart.Ref).postNo }).containsExactly(7L, 8L).inOrder()
        assertThat(d.parts).containsExactly(PostPart.Text("<b>x</b> >>99999999999999"))
    }

    @Test
    fun emptyAndNullTextYieldOneEmptyLine() {
        assertThat(PostText.parse("")).containsExactly(PostLine(false, emptyList()))
        assertThat(PostText.parse(null)).containsExactly(PostLine(false, emptyList()))
    }

    @Test
    fun aDoubleAngleBracketLineIsAReferenceNotAQuote() {
        assertThat(PostText.parse(">>5")[0].isQuote).isFalse()
        assertThat(PostText.parse(">5")[0].isQuote).isTrue()
    }

    @Test
    fun httpsLinksBecomeLinkPartsAndTrailingPunctuationStaysText() {
        val lines = PostText.parse("see https://pics.example/a/1.jpg, ok\n(https://x.test/p?q=1&r=2#h)")
        val first =
            listOf(
                PostPart.Text("see "),
                PostPart.Link("https://pics.example/a/1.jpg"),
                PostPart.Text(", ok"),
            )
        val second =
            listOf(
                PostPart.Text("("),
                PostPart.Link("https://x.test/p?q=1&r=2#h"),
                PostPart.Text(")"),
            )
        assertThat(lines[0].parts).isEqualTo(first)
        assertThat(lines[1].parts).isEqualTo(second)
    }

    @Test
    fun otherSchemesAndUnusableLinksStayLiteralText() {
        val text = "http://insecure.test and javascript:alert(1) and https:// alone\nhttps://" + "a".repeat(600)
        val lines = PostText.parse(text)
        assertThat(lines[0].parts.all { it is PostPart.Text }).isTrue()
        assertThat(lines[1].parts.all { it is PostPart.Text }).isTrue()
    }

    @Test
    fun aLinkStopsAtAnAngleBracketSoAFollowingReferenceStillParses() {
        val line = PostText.parse("https://a.test/x>>12 https://b.test/y")[0]
        assertThat(types(line)).containsExactly("Link", "Ref", "Text", "Link").inOrder()
    }

    @Test
    fun quotesCanContainLinks() {
        val line = PostText.parse(">https://q.test/x")[0]
        assertThat(line.isQuote).isTrue()
        assertThat(line.parts).containsExactly(PostPart.Link("https://q.test/x"))
    }

    @Test
    fun hostileMarkupStaysInertText() {
        val line = PostText.parse("<img src=x onerror=1> https://x.test/<script>")[0]
        assertThat(line.parts.filterIsInstance<PostPart.Link>().map { it.url }).containsExactly("https://x.test/")
        assertThat((line.parts.first() as PostPart.Text).value).startsWith("<img src=x")
    }

    @Test
    fun isSafeLinkAcceptsOnlyBoundedHttpsUrls() {
        assertThat(PostText.isSafeLink("https://x.test/p?q=1#a")).isTrue()
        listOf("http://x.test", "javascript:alert(1)", "https://x.test/a b", "data:text/html,hi", "", null).forEach {
            assertThat(PostText.isSafeLink(it)).isFalse()
        }
        assertThat(PostText.isSafeLink("https://x.test/" + "a".repeat(2000))).isFalse()
    }

    @Test
    fun backlinksListEachReplierOnceInReadingOrder() {
        val posts =
            listOf(
                PostSource(10, "OP"),
                PostSource(11, ">>10 a >>10"),
                PostSource(12, ">>10\n>>11"),
                PostSource(13, ">>13 self >>999 missing"),
            )
        val backlinks = PostText.backlinks(posts)
        assertThat(backlinks[10]).containsExactly(11L, 12L).inOrder()
        assertThat(backlinks[11]).containsExactly(12L)
        assertThat(backlinks).doesNotContainKey(13L)
        assertThat(backlinks).doesNotContainKey(999L)
        assertThat(PostText.backlinks(emptyList())).isEmpty()
    }
}
