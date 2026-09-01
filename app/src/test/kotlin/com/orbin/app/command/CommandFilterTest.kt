package com.orbin.app.command

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The command surface replaces a two-item bottom bar, several top-bar icon sets, a settings hub of
 * seven screens and the settings search screen. It is only that replacement if typing a few letters
 * actually reaches the thing you meant, so the filtering is the part worth pinning down.
 */
class CommandFilterTest {
    @Test
    fun `an empty query offers recents places and actions, not the whole catalogue`() {
        val results = filterCommands(catalogue(), "")

        assertThat(results).isNotEmpty()
        assertThat(
            results.all {
                it is CommandTarget.OpenThread || it is CommandTarget.Go || it is CommandTarget.Act
            },
        ).isTrue()
        // Someone who opened the surface without a word in mind gets useful recent context plus
        // destinations/actions, not every setting and board in the catalogue.
        assertThat(results.none { it is CommandTarget.OpenSetting || it is CommandTarget.OpenBoard }).isTrue()
        assertThat(results.filterIsInstance<CommandTarget.OpenThread>().map { it.label })
            .containsExactly("Automotive detailing general")
    }

    @Test
    fun `one query reaches a setting, a board and a thread at once`() {
        val results = filterCommands(catalogue(), "auto")

        assertThat(
            results.map { it.label },
        ).containsAtLeast("Autoplay videos", "/auto/", "Automotive detailing general")
    }

    @Test
    fun `a prefix match outranks a mention buried in a description`() {
        // Past the filter-feed entry, which leads for every query by design.
        val results = filterCommands(catalogue(), "his").drop(1)

        // "History" starts with it; the /g/ board only mentions it in its description.
        assertThat(results.first().label).isEqualTo("History")
    }

    @Test
    fun `a setting is reachable by the name of the screen it used to live behind`() {
        val results = filterCommands(catalogue(), "Media & Playback")

        assertThat(results.map { it.label }).contains("Autoplay videos")
    }

    @Test
    fun `matching is case insensitive and ignores surrounding space`() {
        assertThat(filterCommands(catalogue(), "  GALLERY ").map { it.label }).contains("Gallery")
    }

    @Test
    fun `the three actions the old feed chrome carried are all reachable`() {
        val labels = filterCommands(catalogue(), "").map { it.label }

        assertThat(labels).containsAtLeast("Refresh feed", "Scroll to top", "Lock Orbin")
    }

    @Test
    fun `any query offers to filter the feed by it, carrying the text`() {
        // This is where the previous feed's search bar went: the text field already exists, so the
        // feature did not need a second one.
        val first = filterCommands(catalogue(), "thinkpad").first()

        assertThat(first).isInstanceOf(CommandTarget.Act::class.java)
        assertThat((first as CommandTarget.Act).action).isEqualTo(CommandAction.FILTER_FEED)
        assertThat(first.query).isEqualTo("thinkpad")
    }

    @Test
    fun `a query matching nothing else still offers to filter the feed`() {
        // The feed is the one place the query might still match, so this result is never dropped.
        val results = filterCommands(catalogue(), "zzzzz")

        assertThat(results).hasSize(1)
        assertThat((results.single() as CommandTarget.Act).action).isEqualTo(CommandAction.FILTER_FEED)
    }

    @Test
    fun `ids are unique, so a board and a setting sharing a label stay distinct`() {
        val ids = catalogue().map { it.commandId() }

        assertThat(ids).containsNoDuplicates()
    }

    private fun catalogue(): List<CommandTarget> =
        staticTargets() +
            listOf(
                CommandTarget.OpenThread("Automotive detailing general", "/o/", "fourchan", "o", 1L),
                CommandTarget.OpenBoard("/auto/", "Automobiles", "fourchan", "auto", "Automobiles"),
                CommandTarget.OpenBoard("/g/", "Technology and its history", "fourchan", "g", "Technology"),
                CommandTarget.OpenSetting("Autoplay videos", "Media & playback", "autoplay"),
                CommandTarget.OpenSetting("Hidden tags", "Content & feed", "hiddenTags"),
            )
}
