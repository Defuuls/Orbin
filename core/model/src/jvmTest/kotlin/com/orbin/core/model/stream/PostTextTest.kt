package com.orbin.core.model.stream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PostTextTest {
    private fun types(line: PostLine) = line.parts.map { it::class.simpleName }

    @Test
    fun refsGreentextAndPlainTextBecomeSegments() {
        val lines = PostText.parse(">>12 agreed\n>quoted line\n>>7>>8\n<b>x</b> >>99999999999999")
        val (a, b, c) = lines
        val d = lines[3]
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

class PostTextBoundTest {
    private fun lines(vararg text: String) = text.map { PostLine(false, listOf(PostPart.Text(it))) }

    private fun textOf(lines: List<PostLine>) =
        lines.joinToString("\n") { line ->
            line.parts.joinToString("") {
                when (it) {
                    is PostPart.Text -> it.value
                    is PostPart.Ref -> it.raw
                    is PostPart.Link -> it.url
                }
            }
        }

    @Test
    fun textWithinTheLimitsIsReturnedUnchanged() {
        val input = lines("one", "two")
        assertThat(PostText.bound(input, maxChars = 100, maxLines = 10)).isEqualTo(input)
        assertThat(PostText.bound(emptyList(), 10, 10)).isEmpty()
    }

    @Test
    fun exactlyAtTheLimitsAddsNothing() {
        val input = lines("abc", "de")
        assertThat(PostText.bound(input, maxChars = 5, maxLines = 2)).isEqualTo(input)
    }

    @Test
    fun tooManyLinesAreCutWithAnEllipsisLine() {
        val out = PostText.bound(lines("a", "b", "c", "d"), maxChars = 100, maxLines = 2)
        assertThat(textOf(out)).isEqualTo("a\nb\n…")
    }

    @Test
    fun tooMuchTextIsCutMidLineAndMarked() {
        val out = PostText.bound(lines("abcdefghij", "klm"), maxChars = 4, maxLines = 10)
        assertThat(textOf(out)).isEqualTo("abcd\n…")
    }

    @Test
    fun aReferenceOrLinkIsKeptWholeOrDroppedNeverSplit() {
        val line =
            PostLine(
                false,
                listOf(PostPart.Text("hi "), PostPart.Ref(">>12345", 12345L), PostPart.Link("https://example.com/x")),
            )
        val kept = PostText.bound(listOf(line), maxChars = 10, maxLines = 5)
        assertThat(textOf(kept)).isEqualTo("hi >>12345\n…")
        val dropped = PostText.bound(listOf(line), maxChars = 5, maxLines = 5)
        assertThat(textOf(dropped)).isEqualTo("hi \n…")
    }

    @Test
    fun cuttingNeverSplitsASurrogatePair() {
        val out = PostText.bound(lines("ab😀cd"), maxChars = 3, maxLines = 5)
        assertThat(textOf(out)).isEqualTo("ab\n…")
    }

    @Test
    fun zeroLimitsLeaveOnlyTheMarker() {
        assertThat(textOf(PostText.bound(lines("abc"), maxChars = 0, maxLines = 0))).isEqualTo("…")
        assertThat(textOf(PostText.bound(lines("abc"), maxChars = -1, maxLines = -1))).isEqualTo("…")
    }

    @Test
    fun aQuoteLineKeepsItsFlagWhenCut() {
        val out = PostText.bound(listOf(PostLine(true, listOf(PostPart.Text("abcdef")))), maxChars = 3, maxLines = 5)
        assertThat(out.first().isQuote).isTrue()
    }
}
