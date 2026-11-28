package com.orbin.app.pulse

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.stream.StreamLens
import com.orbin.uipulse.PulseLineUi
import org.junit.Test

class PulseCommandsTest {
    private val words = PulseCommandWords("Refresh", "Back", { it.label })
    private val lines =
        listOf(
            PulseLineUi(id = "t:a|g|1", age = "1h", title = "Seed saving", sub = "/g/ · Garden", trailing = "7"),
            PulseLineUi(id = "t:a|g|2", age = "2h", title = "Night service", sub = "/trn/ · Trains", trailing = "1"),
        )

    @Test
    fun commandsListRefreshBackOnlyWhenThereIsSomewhereToGoBackToAndEveryLens() {
        assertThat(pulseCommands(showBack = false, words).map { it.id })
            .containsExactly("refresh", "home", "watched", "saved", "settings")
            .inOrder()
        assertThat(pulseCommands(showBack = true, words).map { it.id })
            .containsExactly("refresh", "back", "home", "watched", "saved", "settings")
            .inOrder()
    }

    @Test
    fun anIdResolvesToItsActionAndAnythingElseToNothing() {
        assertThat(resolveAction("refresh", showBack = false)).isEqualTo(PulseAction.Refresh)
        assertThat(resolveAction("back", showBack = true)).isEqualTo(PulseAction.Back)
        assertThat(resolveAction("back", showBack = false)).isNull()
        assertThat(resolveAction("settings", showBack = false))
            .isEqualTo(PulseAction.OpenLens(StreamLens.Settings))
        assertThat(resolveAction("clips", showBack = false)).isNull()
        assertThat(resolveAction("feeds", showBack = false)).isNull()
        assertThat(resolveAction("nonsense", showBack = false)).isNull()
        assertThat(resolveAction(null, showBack = false)).isNull()
        assertThat(resolveAction("", showBack = true)).isNull()
    }

    @Test
    fun plainTextFiltersTheStreamAndShowsNoSuggestions() {
        val view = pulseView(lines, "night", pulseCommands(false, words))
        assertThat(view.lines.map { it.id }).containsExactly("t:a|g|2")
        assertThat(view.suggestions).isEmpty()
    }

    @Test
    fun aBlankInputShowsTheWholeStream() {
        assertThat(pulseView(lines, "", pulseCommands(false, words)).lines).isEqualTo(lines)
        assertThat(pulseView(lines, "   ", pulseCommands(false, words)).lines).isEqualTo(lines)
    }

    @Test
    fun aFilterAlsoMatchesTheDetailLine() {
        assertThat(pulseView(lines, "trains", pulseCommands(false, words)).lines.map { it.id })
            .containsExactly("t:a|g|2")
    }

    @Test
    fun aSlashSearchesCommandsAndLeavesTheStreamAlone() {
        val view = pulseView(lines, "/set", pulseCommands(false, words))
        assertThat(view.lines).isEqualTo(lines)
        assertThat(view.suggestions.map { it.id }).containsExactly("settings")
    }

    @Test
    fun aBareSlashSuggestsEveryCommand() {
        assertThat(pulseView(lines, "/", pulseCommands(true, words)).suggestions).hasSize(6)
    }

    @Test
    fun aSlashWithNoMatchSuggestsNothing() {
        assertThat(pulseView(lines, "/zzzz", pulseCommands(false, words)).suggestions).isEmpty()
    }

    @Test
    fun submittingRunsTheBestSuggestionOnlyForACommand() {
        val commands = pulseCommands(false, words)
        assertThat(submitAction("/ref", commands, showBack = false)).isEqualTo(PulseAction.Refresh)
        assertThat(submitAction("/sav", commands, showBack = false))
            .isEqualTo(PulseAction.OpenLens(StreamLens.Saved))
        assertThat(submitAction("/zzzz", commands, showBack = false)).isNull()
        assertThat(submitAction("night", commands, showBack = false)).isNull()
        assertThat(submitAction("", commands, showBack = false)).isNull()
    }

    @Test
    fun submittingNeverRunsBackWhereThereIsNoBack() {
        val commands = pulseCommands(true, words)
        assertThat(submitAction("/back", commands, showBack = false)).isNull()
        assertThat(submitAction("/back", commands, showBack = true)).isEqualTo(PulseAction.Back)
    }
}
