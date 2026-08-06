package com.orbin.app.navigation

import androidx.compose.runtime.saveable.SaverScope
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The two-pane catalog remembers its open thread through [threadRouteSaver], and that is the only
 * thing standing between a tablet rotation and the reader losing what they were reading — a 10"
 * tablet crosses the two-pane width threshold on an ordinary turn of the wrist.
 *
 * Failures here are silent in a way that is hard to notice by hand: a transposed index restores a
 * *different* thread rather than crashing, and a Long narrowed to Int only misbehaves on boards
 * whose post numbers have grown past two billion.
 */
class ThreadRouteSaverTest {
    // Everything this saver produces is Bundle-friendly, so nothing is ever rejected.
    private val scope = SaverScope { true }

    private fun roundTrip(thread: Route.Thread?): Route.Thread? {
        // Saver.save is declared as a member extension on SaverScope, so both receivers are needed.
        val saved = with(threadRouteSaver) { with(scope) { save(thread) } }
        return threadRouteSaver.restore(checkNotNull(saved) { "saver returned null for $thread" })
    }

    @Test
    fun aSelectedThreadSurvivesIntact() {
        val thread = Route.Thread(provider = "4chan", board = "g", thread = 98765L, title = "Sticky")

        assertThat(roundTrip(thread)).isEqualTo(thread)
    }

    /** Fields are positional, so a transposition restores a plausible-looking wrong thread. */
    @Test
    fun fieldsDoNotSwapPlaces() {
        // Provider and board are both short lowercase strings and are the easiest pair to confuse.
        val restored = roundTrip(Route.Thread(provider = "8kun", board = "b", thread = 1L, title = "t"))!!

        assertThat(restored.provider).isEqualTo("8kun")
        assertThat(restored.board).isEqualTo("b")
    }

    /** Post numbers outgrow Int on long-lived boards; narrowing would corrupt them silently. */
    @Test
    fun aThreadIdBeyondIntRangeIsNotNarrowed() {
        val big = Int.MAX_VALUE.toLong() + 1_000L

        assertThat(roundTrip(Route.Thread("4chan", "a", big, "t"))!!.thread).isEqualTo(big)
    }

    /**
     * "No thread selected" is encoded as the empty list, and `listSaver` turns that into a null
     * saved value — nothing is written, so `rememberSaveable` re-runs its initialiser and lands
     * back on null. The encoding only works because of that collapse, so pin it here: if it ever
     * stored an empty list instead, `restore` would index into it and throw.
     */
    @Test
    fun noSelectionSavesNothingAtAll() {
        val saved = with(threadRouteSaver) { with(scope) { save(null) } }

        assertThat(saved).isNull()
    }

    /** An empty title is a real case — many threads have no subject — and must not read as null. */
    @Test
    fun anEmptyTitleIsStillAThread() {
        val restored = roundTrip(Route.Thread("4chan", "a", 7L, ""))

        assertThat(restored).isNotNull()
        assertThat(restored!!.title).isEmpty()
    }
}
