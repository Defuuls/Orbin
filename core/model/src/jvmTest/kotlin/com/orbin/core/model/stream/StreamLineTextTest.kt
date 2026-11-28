package com.orbin.core.model.stream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreamIdsTest {
    @Test
    fun aThreadIdRoundTrips() {
        val id = StreamIds.thread("4chan", "g", 1234L)
        assertThat(id).isEqualTo("t:4chan|g|1234")
        assertThat(StreamIds.parseThread(id)).isEqualTo(ThreadRef("4chan", "g", 1234L))
    }

    @Test
    fun partsThatWouldMakeTheIdAmbiguousGiveNoId() {
        assertThat(StreamIds.thread("a|b", "g", 1L)).isNull()
        assertThat(StreamIds.thread("a", "g|h", 1L)).isNull()
        assertThat(StreamIds.thread("", "g", 1L)).isNull()
        assertThat(StreamIds.thread("a", "", 1L)).isNull()
        assertThat(StreamIds.thread("a", "g", 0L)).isNull()
        assertThat(StreamIds.thread("a", "g", -5L)).isNull()
    }

    @Test
    fun parsingNeverTrustsItsInput() {
        listOf(
            null,
            "",
            "t:",
            "t:a|g",
            "t:a|g|1|2",
            "t:a|g|x",
            "t:a|g|0",
            "t:a|g|-1",
            "t:|g|1",
            "t:a||1",
            "p:12",
            "T:a|g|1",
            "t:a|g|99999999999999999999",
        ).forEach { assertThat(StreamIds.parseThread(it)).isNull() }
    }

    @Test
    fun aPostIdRoundTripsAndNothingElseParsesAsOne() {
        assertThat(StreamIds.parsePost("p:42")).isEqualTo(42L)
        listOf(null, "", "p:", "p:0", "p:-3", "p:x", "t:a|g|1", "P:42", "p:99999999999999999999")
            .forEach { assertThat(StreamIds.parsePost(it)).isNull() }
    }

    @Test
    fun aPostIdIsNotAThreadId() {
        assertThat(StreamIds.post(42L)).isEqualTo("p:42")
        assertThat(StreamIds.parseThread(StreamIds.post(42L))).isNull()
    }
}

class StreamTextTest {
    private val now = 1_000_000_000_000L

    @Test
    fun ageUsesTheLargestWholeUnit() {
        assertThat(StreamText.age(now, now - 30_000L)).isEqualTo("now")
        assertThat(StreamText.age(now, now - 59_999L)).isEqualTo("now")
        assertThat(StreamText.age(now, now - 60_000L)).isEqualTo("1m")
        assertThat(StreamText.age(now, now - 59 * 60_000L)).isEqualTo("59m")
        assertThat(StreamText.age(now, now - 3_600_000L)).isEqualTo("1h")
        assertThat(StreamText.age(now, now - 23 * 3_600_000L)).isEqualTo("23h")
        assertThat(StreamText.age(now, now - 24 * 3_600_000L)).isEqualTo("1d")
        assertThat(StreamText.age(now, now - 6 * 86_400_000L)).isEqualTo("6d")
        assertThat(StreamText.age(now, now - 7 * 86_400_000L)).isEqualTo("1w")
        assertThat(StreamText.age(now, now - 70 * 86_400_000L)).isEqualTo("10w")
    }

    @Test
    fun anUnknownTimeIsBlankAndAFutureTimeIsNow() {
        assertThat(StreamText.age(now, 0L)).isEmpty()
        assertThat(StreamText.age(now, -1L)).isEmpty()
        assertThat(StreamText.age(now, now + 5_000L)).isEqualTo("now")
    }

    @Test
    fun clipCollapsesWhitespaceAndLeavesShortTextAlone() {
        assertThat(StreamText.clip("  a \n\n b\t c  ", 20)).isEqualTo("a b c")
        assertThat(StreamText.clip("exactly10!", 10)).isEqualTo("exactly10!")
        assertThat(StreamText.clip(null, 10)).isEmpty()
        assertThat(StreamText.clip("   ", 10)).isEmpty()
    }

    @Test
    fun clipEndsWithAnEllipsisWithinTheLimit() {
        val clipped = StreamText.clip("abcdefghijklmnop", 10)
        assertThat(clipped).isEqualTo("abcdefghi…")
        assertThat(clipped.length).isAtMost(10)
        assertThat(StreamText.clip("abc", 0)).isEmpty()
        assertThat(StreamText.clip("abc", -3)).isEmpty()
        assertThat(StreamText.clip("abcdef", 1)).isEqualTo("…")
    }

    @Test
    fun clipNeverSplitsASurrogatePair() {
        val text = "ab" + "😀".repeat(10)
        (1..text.length).forEach { max ->
            val clipped = StreamText.clip(text, max)
            assertThat(clipped.length).isAtMost(max)
            clipped.forEachIndexed { index, ch ->
                if (ch.isHighSurrogate()) assertThat(clipped.getOrNull(index + 1)?.isLowSurrogate()).isTrue()
            }
        }
    }

    @Test
    fun clipTrimsSpaceBeforeTheEllipsis() {
        assertThat(StreamText.clip("hello world and more", 7)).isEqualTo("hello…")
    }

    @Test
    fun aHeadlinePrefersTheSubjectThenTheFirstLineThenTheNumber() {
        assertThat(StreamText.headline("Subject", "body", 9L, 50)).isEqualTo("Subject")
        assertThat(StreamText.headline(null, "\n\n  first line \nsecond", 9L, 50)).isEqualTo("first line")
        assertThat(StreamText.headline("  ", "text", 9L, 50)).isEqualTo("text")
        assertThat(StreamText.headline(null, null, 9L, 50)).isEqualTo("No.9")
        assertThat(StreamText.headline("", " \n ", 9L, 50)).isEqualTo("No.9")
    }

    @Test
    fun aHeadlineIsClippedAndHostileTextStaysText() {
        val hostile = "<script>alert(1)</script> " + "x".repeat(300)
        val headline = StreamText.headline(hostile, null, 1L, 40)
        assertThat(headline.length).isAtMost(40)
        assertThat(headline).startsWith("<script>")
    }
}

class StreamFilterTest {
    private val items = listOf("Seed saving", "Raised bed layout", "Night service", "Soil pH testing")

    @Test
    fun aBlankQueryKeepsEverythingInOrder() {
        assertThat(StreamFilter.apply(items, "  ") { it }).isEqualTo(items)
        assertThat(StreamFilter.apply(items, null) { it }).isEqualTo(items)
    }

    @Test
    fun matchingKeepsTheOriginalOrder() {
        assertThat(StreamFilter.apply(items, "ed") { it })
            .containsExactly("Seed saving", "Raised bed layout")
            .inOrder()
        assertThat(StreamFilter.apply(items, "zzzz") { it }).isEmpty()
    }

    @Test
    fun matchingIgnoresCase() {
        assertThat(StreamFilter.apply(items, "RAISED") { it }).containsExactly("Raised bed layout")
    }
}
