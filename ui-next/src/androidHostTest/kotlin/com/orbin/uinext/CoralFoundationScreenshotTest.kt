package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual shared foundation chrome; platform copy and fixed local records avoid live dependencies. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w390dp-h844dp-mdpi")
class CoralFoundationScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lock() = capture("lock") { LockScreen(message = null, unlocking = false, onUnlock = {}) }

    @Test
    fun safeMode() =
        capture("safe_mode", dark = true) {
            SafeModeScreen(safeModeCopy, onExportDiagnostics = {}, onResetLocalData = {}, onContinueAnyway = {})
        }

    @Test
    fun whatsNew() = capture("whats_new", dialog = true) { WhatsNewDialog(releaseNotes, onDismiss = {}) }

    @Test
    fun confirmationAtMaximumText() =
        capture("confirmation_font_200", dark = true, fontScale = 2f, dialog = true) {
            OrbinConfirmDialog(
                title = "Clear download history?",
                message = "This removes every entry from the list below. It doesn't delete the files themselves.",
                confirmLabel = "Clear",
                onConfirm = {},
                onDismiss = {},
            )
        }

    @Test
    fun snackbarAtMaximumText() {
        composeRule.mainClock.autoAdvance = false
        capture("snackbar_font_200", fontScale = 2f) {
            val host = remember { NextSnackbarHostState() }
            LaunchedEffect(host) { host.showSnackbar("Download queued", withDismissAction = true) }
            Box(Modifier.fillMaxSize()) {
                ReaderAppBar(title = "Downloads")
                NextSnackbarHost(host, Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    @Test
    fun downloadCardsAtMaximumText() =
        capture("downloads_font_200", dark = true, fontScale = 2f) { DownloadCardsFixture() }

    private fun capture(
        name: String,
        dark: Boolean = false,
        fontScale: Float = 1f,
        dialog: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            NextTheme(darkTheme = dark, platform = NextPlatform.IOS, fontScale = fontScale) {
                Box(Modifier.fillMaxSize().background(reader.background)) { content() }
            }
        }
        if (!composeRule.mainClock.autoAdvance) composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()
        val root = if (dialog) composeRule.onNode(isDialog()) else composeRule.onRoot()
        val theme = if (dark) "dark" else "light"
        root.captureRoboImage("src/androidHostTest/screenshots/coral_foundation_${theme}_$name.png")
    }
}

@Composable
private fun DownloadCardsFixture() {
    Column(Modifier.fillMaxSize()) {
        ReaderAppBar(title = "Downloads")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DownloadRow(
                fileName = "fourchan-g-12345-landscape.webm",
                status = "Downloading",
                progress = 0.42f,
                active = true,
                retryLabel = "Retry",
            )
            DownloadRow(
                fileName = "A longer source-qualified attachment name.webm",
                status = "Failed",
                progress = null,
                active = false,
                retryLabel = "Retry",
                onRetry = {},
            )
            DownloadRow(
                fileName = "landscape.jpg",
                status = "Saved",
                progress = null,
                active = false,
                retryLabel = "Retry",
            )
        }
    }
}

/** The Android/iOS safe-mode copy is supplied by the hosts in production. */
private val safeModeCopy =
    SafeModeText(
        title = "Orbin didn't start",
        explanation =
            "The last two launches stopped before the app was usable, so it's starting in safe mode " +
                "instead of trying again.",
        saveFirst =
            "Saving the crash details first is worth doing even if you plan to reset — it's the only record " +
                "of what went wrong, and it stays on your device until you share it.",
        saveCrashDetails = "Save crash details",
        reset = "Reset local data",
        tryNormally = "Try starting normally",
        resetDialogTitle = "Reset local data?",
        resetDialogText =
            "Deletes bookmarks, reading history and download history from this device. Your settings are kept. " +
                "This cannot be undone.",
        resetConfirm = "Reset",
        cancel = "Cancel",
    )

private val releaseNotes =
    ReleaseNotes(
        version = "1.0-Orbin",
        sections =
            listOf(
                ReleaseNoteSection("Changed", listOf("Refreshed reader cards and navigation.")),
                ReleaseNoteSection("Fixed", listOf("Kept saved board follows when reopening the app.")),
            ),
    )
