package com.orbin.uinext

import androidx.compose.ui.test.assertIsSelected
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
    fun `all four tabs dispatch distinct destinations`() {
        val selected = mutableListOf<NextDestination>()
        composeRule.setContent {
            NextTheme { DestinationPill(NextDestination.SAVED, selected::add) }
        }
        composeRule.onNodeWithContentDescription("Saved").assertIsSelected()
        listOf("Home", "Search", "Boards", "Saved").forEach { label ->
            composeRule.onNodeWithContentDescription(label).performClick()
        }
        assertThat(selected)
            .containsExactly(
                NextDestination.FEED,
                NextDestination.SEARCH,
                NextDestination.BOARDS,
                NextDestination.SAVED,
            ).inOrder()
    }

    @Test
    fun `secondary pages do not claim a primary tab`() {
        assertThat(NextDestination.DOWNLOADS.drawsPill()).isFalse()
        assertThat(NextDestination.SETTINGS.drawsPill()).isFalse()
        assertThat(NextDestination.SEARCH.drawsPill()).isTrue()
        assertThat(NextDestination.SAVED.drawsPill()).isTrue()
    }
}
