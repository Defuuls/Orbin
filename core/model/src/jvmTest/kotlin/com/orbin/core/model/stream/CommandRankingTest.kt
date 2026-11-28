package com.orbin.core.model.stream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CommandRankingTest {
    private fun cmd(
        label: String,
        vararg keywords: String,
    ) = PaletteCommand(label, label, keywords.toList())

    @Test
    fun scoresOrderPrefixOverWordPrefixOverSubstringOverLetters() {
        val prefix = CommandRanking.scoreText("Theme: Dark", "the")
        val word = CommandRanking.scoreText("Go to Theme", "the")
        val substring = CommandRanking.scoreText("Bathe now", "the")
        val letters = CommandRanking.scoreText("Turn on high energy", "the")
        assertThat(prefix).isGreaterThan(word)
        assertThat(word).isGreaterThan(substring)
        assertThat(substring).isGreaterThan(letters)
        assertThat(letters).isGreaterThan(0.0)
        assertThat(CommandRanking.scoreText("Refresh", "zzz")).isEqualTo(0.0)
    }

    @Test
    fun aBlankQueryMatchesEverythingEquallyAndCaseIsIgnored() {
        assertThat(CommandRanking.scoreText("Anything", "   ")).isEqualTo(1.0)
        assertThat(CommandRanking.scoreText("Anything", null)).isEqualTo(1.0)
        assertThat(CommandRanking.scoreText("REFRESH", "refr")).isEqualTo(CommandRanking.scoreText("refresh", "REFR"))
    }

    @Test
    fun shorterLabelsWinAmongEqualPrefixes() {
        assertThat(CommandRanking.scoreText("Go", "go")).isGreaterThan(CommandRanking.scoreText("Go to Feeds", "go"))
    }

    @Test
    fun keywordsFindCommandsButALabelMatchWinsOverAKeywordMatch() {
        val commands =
            listOf(
                cmd("Slow connection", "offline"),
                cmd("Go offline mode"),
                cmd("Refresh everything", "reload"),
            )
        val offline = CommandRanking.rank(commands, "offline").map { it.label }
        assertThat(offline).containsExactly("Go offline mode", "Slow connection").inOrder()
        assertThat(CommandRanking.rank(commands, "reload").map { it.label }).containsExactly("Refresh everything")
    }

    @Test
    fun tiesKeepTheOriginalOrder() {
        val commands = listOf(cmd("Alpha"), cmd("Alpha"), cmd("Alpha")).mapIndexed { i, c -> c.copy(id = "$i") }
        assertThat(CommandRanking.rank(commands, "").map { it.id }).containsExactly("0", "1", "2").inOrder()
    }

    @Test
    fun limitIsAppliedAndANegativeLimitYieldsNothing() {
        val commands = (1..20).map { cmd("Item $it") }
        assertThat(CommandRanking.rank(commands, "item")).hasSize(CommandRanking.DEFAULT_LIMIT)
        assertThat(CommandRanking.rank(commands, "item", limit = 12)).hasSize(12)
        assertThat(CommandRanking.rank(commands, "item", limit = -1)).isEmpty()
    }

    @Test
    fun noMatchReturnsAnEmptyList() {
        assertThat(CommandRanking.rank(listOf(cmd("Refresh")), "qqq")).isEmpty()
        assertThat(CommandRanking.rank(emptyList(), "x")).isEmpty()
    }

    @Test
    fun aSlashStartsACommandAndAnythingElseFilters() {
        assertThat(CommandRanking.parseInput("/theme dark")).isEqualTo(PaletteInput.Command("theme dark"))
        assertThat(CommandRanking.parseInput("   /go  ")).isEqualTo(PaletteInput.Command("go"))
        assertThat(CommandRanking.parseInput("/")).isEqualTo(PaletteInput.Command(""))
        assertThat(CommandRanking.parseInput("  tomato ")).isEqualTo(PaletteInput.Filter("tomato"))
        assertThat(CommandRanking.parseInput("a/b")).isEqualTo(PaletteInput.Filter("a/b"))
        assertThat(CommandRanking.parseInput(null)).isEqualTo(PaletteInput.Filter(""))
    }
}
