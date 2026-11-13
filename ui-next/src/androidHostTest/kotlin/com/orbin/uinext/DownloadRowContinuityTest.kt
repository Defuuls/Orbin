package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadRowContinuityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun largeTextStacksRetryBelowTheFilenameWithoutLosingItsTouchTarget() {
        var retries = 0
        val fileName = "A long source-qualified file name.webm"
        compose.setContent {
            NextTheme(fontScale = 2f) {
                Box(Modifier.width(280.dp)) {
                    DownloadRow(
                        fileName = fileName,
                        status = "Failed",
                        progress = null,
                        active = false,
                        retryLabel = "Retry",
                        retryDescription = "Retry download",
                        onRetry = { retries++ },
                    )
                }
            }
        }
        val file = compose.onNodeWithText(fileName).getUnclippedBoundsInRoot()
        val retry = compose.onNodeWithContentDescription("Retry download")
        val retryBounds = retry.getUnclippedBoundsInRoot()
        assertTrue(retryBounds.top >= file.bottom)
        assertTrue(retryBounds.left >= 0.dp && retryBounds.right <= 280.dp)
        retry.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, retries)
    }
}
