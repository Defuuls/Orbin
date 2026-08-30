package com.orbin.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The feed's counts, resolved the way the screen resolves them.
 *
 * These used to be plain string concatenation with a helper that appended an "s", so the check was
 * a plain equality on a pure function. They are plural resources now, which is the fix — but a
 * resource can be wired up wrongly just as easily as a helper can be written wrongly, so the test
 * follows them into a composition rather than being deleted along with the helper.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedCountsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `one thread on one board reads singular throughout`() {
        assertThat(resolve({ feedSubtitle(threads = 1, boards = 1) }).single())
            .isEqualTo("1 thread across 1 board")
    }

    @Test
    fun `many threads across many boards read plural throughout`() {
        assertThat(resolve({ feedSubtitle(threads = 8, boards = 7) }).single())
            .isEqualTo("8 threads across 7 boards")
    }

    @Test
    fun `the rail's board count inflects on its own count`() {
        // Zero takes the plural form in English, which is the case an "if (count == 1)" helper
        // gets right by accident and a locale with a zero form gets wrong.
        val (one, none) = resolve({ boardCountLabel(1) }, { boardCountLabel(0) })
        assertThat(one).isEqualTo("1 board")
        assertThat(none).isEqualTo("0 boards")
    }

    /**
     * Resolves each value in one composition.
     *
     * The rule allows a single `setContent` per test, so everything a test needs is composed
     * together rather than a composition per value.
     */
    private fun resolve(vararg values: @Composable () -> String): List<String> {
        val resolved = mutableListOf<String>()
        composeRule.setContent { values.forEach { resolved += it() } }
        composeRule.waitForIdle()
        return resolved.toList()
    }
}
