package com.orbin.ios

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.orbin.core.model.DownloadRecord
import com.orbin.core.model.DownloadStatus
import com.orbin.uinext.DownloadTags
import com.orbin.uinext.NextPlatform
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ReaderAppBarTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class DownloadsDestinationContinuityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun longHistoriesComposeOnlyNearbyCardsAndRetryTheSelectedRecord() {
        val records =
            List(200) {
                record(
                    it.toLong(),
                    if (it ==
                        199
                    ) {
                        DownloadStatus.FAILED
                    } else {
                        DownloadStatus.COMPLETED
                    },
                )
            }
        var retried = -1L
        compose.setContent {
            NextTheme(platform = NextPlatform.IOS) {
                DownloadsScreen(records, onRetry = { retried = it }, onClear = {}, onDestination = {})
            }
        }
        compose.onNodeWithTag(DownloadTags.row(199L)).assertDoesNotExist()
        // The first lazy item retains the iOS-specific destination guidance.
        compose.onNodeWithTag(DownloadTags.LIST).performScrollToIndex(records.size)
        compose.onNodeWithTag(DownloadTags.row(199L)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Retry download").performClick()
        assertEquals(199L, retried)
    }

    @Test
    fun clearingWaitsForConfirmationAndKeepsTheEmptyStateReachable() {
        var records by mutableStateOf(listOf(record(7L, DownloadStatus.COMPLETED)))
        var clears = 0
        compose.setContent {
            NextTheme(platform = NextPlatform.IOS) {
                DownloadsScreen(
                    records,
                    onRetry = {},
                    onClear = {
                        clears++
                        records = emptyList()
                    },
                    onDestination = {},
                )
            }
        }
        compose.onNodeWithTag(ReaderAppBarTags.OVERFLOW).performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Clear the list?").assertIsDisplayed()
        assertEquals(0, clears)
        compose.onNodeWithText("Delete").performClick()
        assertEquals(1, clears)
        compose.onNodeWithText("No downloads yet").assertIsDisplayed()
        compose.onNodeWithText("Files you save from a thread appear here.").assertIsDisplayed()
    }

    private fun record(
        id: Long,
        status: DownloadStatus,
    ) = DownloadRecord(
        id = id,
        url = "https://example.invalid/clip-$id.webm",
        fileName = "clip-$id.webm",
        status = status,
        createdAtMillis = id,
    )
}
