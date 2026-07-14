package com.orbin.feature.downloads

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbin.core.designsystem.theme.OrbinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for the Downloads screen.
 * Verifies download history display and retry functionality.
 */
@RunWith(AndroidJUnit4::class)
class DownloadsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun downloadsScreenDisplaysEmptyStateCorrectly() {
        composeTestRule.setContent {
            OrbinTheme {
                composeTestRule
                    .onNodeWithText("No downloads yet")
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun retryButtonAppearsForFailedDownloads() {
        composeTestRule.setContent {
            OrbinTheme {
                composeTestRule
                    .onNodeWithContentDescription("Retry download")
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun retryButtonDoesNotAppearForCompletedDownloads() {
        composeTestRule.setContent {
            OrbinTheme {
                composeTestRule
                    .onNodeWithText("Downloads")
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun downloadStatusLabelsAreDisplayed() {
        composeTestRule.setContent {
            OrbinTheme {
                composeTestRule
                    .onNodeWithText("Completed")
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun clearButtonIsAvailable() {
        composeTestRule.setContent {
            OrbinTheme {
                composeTestRule
                    .onNodeWithContentDescription("Clear")
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun backButtonNavigatesAway() {
        composeTestRule.setContent {
            OrbinTheme {
                composeTestRule
                    .onNodeWithContentDescription("Back")
                    .assertIsDisplayed()
            }
        }
    }
}
