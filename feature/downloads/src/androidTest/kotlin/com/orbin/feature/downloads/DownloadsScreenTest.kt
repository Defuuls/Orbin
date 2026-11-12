package com.orbin.feature.downloads

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbin.core.designsystem.theme.OrbinTheme
import com.orbin.core.model.DownloadRecord
import com.orbin.core.model.DownloadStatus
import com.orbin.core.testing.repository.FakeDownloadRepository
import com.orbin.uinext.ReaderAppBarTags
import com.orbin.uinext.tokens.ReaderSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        var backCalls = 0
        setContent(record(1L, "clip.webm", DownloadStatus.COMPLETED), onBack = { backCalls++ })

        composeTestRule
            .onNodeWithContentDescription("Back")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(ReaderSize.touchTarget)
            .assertHeightIsAtLeast(ReaderSize.touchTarget)
            .performClick()
        composeTestRule.runOnIdle { assertEquals(1, backCalls) }
        composeTestRule
            .onNodeWithTag(ReaderAppBarTags.OVERFLOW)
            .assertWidthIsAtLeast(ReaderSize.touchTarget)
            .assertHeightIsAtLeast(ReaderSize.touchTarget)
        openClearMenu()
            .assertWidthIsAtLeast(ReaderSize.touchTarget)
            .assertHeightIsAtLeast(ReaderSize.touchTarget)
    }

    @Test
    fun clearingAsksForConfirmationFirst() {
        val repository = setContent(record(1L, "clip.webm", DownloadStatus.COMPLETED))

        openClearMenu().performClick()

        composeTestRule.onNodeWithText("Clear download history?").assertIsDisplayed()
        composeTestRule.onNodeWithText("clip.webm").assertIsDisplayed()
        assertDownloadIds(repository, listOf(1L))
    }

    @Test
    fun clearingRemovesEveryDownload() {
        val repository =
            setContent(
                record(1L, "clip.webm", DownloadStatus.COMPLETED),
                record(2L, "other.webm", DownloadStatus.FAILED),
            )

        openClearMenu().performClick()
        composeTestRule.onNodeWithText("Clear download history?").assertIsDisplayed()
        composeTestRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.onNodeWithText("No downloads yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("clip.webm").assertDoesNotExist()
        composeTestRule.onNodeWithText("other.webm").assertDoesNotExist()
        assertDownloadIds(repository, emptyList())
    }

    @Test
    fun dismissingTheConfirmationKeepsTheDownload() {
        val repository = setContent(record(1L, "clip.webm", DownloadStatus.COMPLETED))

        openClearMenu().performClick()
        composeTestRule.onNodeWithText("Clear download history?").assertIsDisplayed()
        composeTestRule.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.onNodeWithText("clip.webm").assertIsDisplayed()
        assertDownloadIds(repository, listOf(1L))
    }

    private fun openClearMenu(): SemanticsNodeInteraction {
        composeTestRule.onNodeWithTag(ReaderAppBarTags.OVERFLOW).assertIsDisplayed().performClick()
        return composeTestRule.onNodeWithText("Clear").assertIsDisplayed()
    }

    private fun assertDownloadIds(
        repository: FakeDownloadRepository,
        expected: List<Long>,
    ) {
        composeTestRule.runOnIdle {
            assertEquals(expected, runBlocking { repository.observeDownloads().first() }.map { it.id })
        }
    }

    private fun setContent(
        vararg records: DownloadRecord,
        onBack: () -> Unit = {},
    ): FakeDownloadRepository {
        val repository = FakeDownloadRepository(records.toList())
        val viewModel = DownloadsViewModel(repository)
        composeTestRule.setContent {
            OrbinTheme {
                DownloadsScreen(onBack = onBack, viewModel = viewModel)
            }
        }
        return repository
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
