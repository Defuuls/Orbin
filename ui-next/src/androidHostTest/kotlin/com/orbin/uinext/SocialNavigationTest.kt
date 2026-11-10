package com.orbin.uinext

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Each visible tab must dispatch its own destination, including the new Search and Saved tabs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SocialNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `all five tabs dispatch distinct destinations`() {
        val selected = mutableListOf<NextDestination>()
        composeRule.setContent {
            NextTheme { DestinationPill(NextDestination.SAVED, selected::add) }
        }
        composeRule.onNodeWithContentDescription("Saved").assertIsSelected()
        listOf("Home", "Watched", "Boards", "Search", "Saved").forEach { label ->
            composeRule.onNodeWithContentDescription(label).performClick()
        }
        assertThat(selected)
            .containsExactly(
                NextDestination.FEED,
                NextDestination.WATCHED,
                NextDestination.BOARDS,
                NextDestination.SEARCH,
                NextDestination.SAVED,
            ).inOrder()
    }

    @Test
    fun `the center add action opens Boards once and announces selection`() {
        var calls = 0
        var selected: NextDestination? = null
        composeRule.setContent {
            NextTheme {
                DestinationPill(NextDestination.BOARDS, {
                    selected = it
                    calls++
                })
            }
        }
        val home = composeRule.onNodeWithContentDescription("Home").getUnclippedBoundsInRoot()
        val watched = composeRule.onNodeWithContentDescription("Watched").getUnclippedBoundsInRoot()
        val boards = composeRule.onNodeWithContentDescription("Boards").getUnclippedBoundsInRoot()
        val search = composeRule.onNodeWithContentDescription("Search").getUnclippedBoundsInRoot()
        val saved = composeRule.onNodeWithContentDescription("Saved").getUnclippedBoundsInRoot()
        assertThat(home.left.value).isLessThan(watched.left.value)
        assertThat(watched.left.value).isLessThan(boards.left.value)
        assertThat(boards.left.value).isLessThan(search.left.value)
        assertThat(search.left.value).isLessThan(saved.left.value)
        assertThat(((boards.left.value + boards.right.value) / 2)).isWithin(0.5f).of(
            (home.left.value + saved.right.value) / 2,
        )
        composeRule.onNodeWithContentDescription("Boards").assertIsSelected().performClick()
        assertThat(selected).isEqualTo(NextDestination.BOARDS)
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `secondary pages do not claim a primary tab`() {
        assertThat(NextDestination.DOWNLOADS.drawsPill()).isFalse()
        assertThat(NextDestination.SETTINGS.drawsPill()).isFalse()
        assertThat(NextDestination.SEARCH.drawsPill()).isTrue()
        assertThat(NextDestination.SAVED.drawsPill()).isTrue()
    }
}
