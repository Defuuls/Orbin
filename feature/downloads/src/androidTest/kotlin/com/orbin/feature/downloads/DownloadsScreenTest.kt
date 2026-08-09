package com.orbin.feature.downloads

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbin.core.designsystem.theme.OrbinTheme
import com.orbin.core.model.DownloadRecord
import com.orbin.core.model.DownloadStatus
import com.orbin.core.testing.repository.FakeDownloadRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for the Downloads screen.
 *
 * The screen takes its ViewModel as a defaulted parameter, so these supply a real one over an
 * in-memory repository rather than standing up Hilt — the screen is what is under test, not the
 * dependency graph.
 */
@RunWith(AndroidJUnit4::class)
class DownloadsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun anEmptyHistoryShowsTheEmptyState() {
        setContent()

        composeTestRule.onNodeWithText("No downloads yet").assertIsDisplayed()
    }

    @Test
    fun eachDownloadShowsItsFileNameAndStatus() {
        setContent(record(1L, "clip.webm", DownloadStatus.COMPLETED))

        composeTestRule.onNodeWithText("clip.webm").assertIsDisplayed()
        composeTestRule.onNodeWithText("Completed").assertIsDisplayed()
        composeTestRule.onNodeWithText("No downloads yet").assertDoesNotExist()
    }

    /** Retry is the whole point of showing a failed download, so it must be reachable. */
    @Test
    fun aFailedDownloadOffersRetry() {
        setContent(record(1L, "broken.webm", DownloadStatus.FAILED))

        composeTestRule.onNodeWithContentDescription("Retry download").assertIsDisplayed()
    }

    /** Offering retry on a finished download would be noise, and on a running one a bug. */
    @Test
    fun downloadsThatAreNotFailedDoNotOfferRetry() {
        setContent(
            record(1L, "done.webm", DownloadStatus.COMPLETED),
            record(2L, "queued.webm", DownloadStatus.QUEUED),
            record(3L, "running.webm", DownloadStatus.RUNNING),
        )

        composeTestRule.onNodeWithContentDescription("Retry download").assertDoesNotExist()
    }

    @Test
    fun theScreenOffersBackAndClearActions() {
        setContent(record(1L, "clip.webm", DownloadStatus.COMPLETED))

        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Clear").assertIsDisplayed()
    }

    @Test
    fun clearingAsksForConfirmationFirst() {
        setContent(record(1L, "clip.webm", DownloadStatus.COMPLETED))

        composeTestRule.onNodeWithContentDescription("Clear").performClick()

        composeTestRule.onNodeWithText("Clear download history?").assertIsDisplayed()
        composeTestRule.onNodeWithText("clip.webm").assertIsDisplayed()
    }

    @Test
    fun clearingRemovesEveryDownload() {
        setContent(record(1L, "clip.webm", DownloadStatus.COMPLETED))

        composeTestRule.onNodeWithContentDescription("Clear").performClick()
        composeTestRule.onNodeWithText("Delete").performClick()

        composeTestRule.onNodeWithText("No downloads yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("clip.webm").assertDoesNotExist()
    }

    @Test
    fun dismissingTheConfirmationKeepsTheDownload() {
        setContent(record(1L, "clip.webm", DownloadStatus.COMPLETED))

        composeTestRule.onNodeWithContentDescription("Clear").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.onNodeWithText("clip.webm").assertIsDisplayed()
    }

    private fun setContent(vararg records: DownloadRecord) {
        val viewModel = DownloadsViewModel(FakeDownloadRepository(records.toList()))
        composeTestRule.setContent {
            OrbinTheme {
                DownloadsScreen(onBack = {}, viewModel = viewModel)
            }
        }
    }

    private fun record(
        id: Long,
        fileName: String,
        status: DownloadStatus,
    ) = DownloadRecord(
        id = id,
        url = "https://example.invalid/$fileName",
        fileName = fileName,
        status = status,
        createdAtMillis = 1_000L,
    )
}
