package com.orbin.uinext.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.orbin.uinext.components.NextTheme
import com.orbin.uinext.components.ReaderAppBarTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class BrowserScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    @Test
    fun `the frame always shows which site the page is on`() {
        show(BrowserChromeState(title = "Sign in", url = "https://accounts.Example.org/login?next=/"))

        composeRule.onNodeWithText("Sign in").assertIsDisplayed()
        composeRule.onNodeWithText("accounts.example.org").assertIsDisplayed()
    }

    @Test
    fun `an untitled page is titled with its host`() {
        show(BrowserChromeState(url = "https://example.org/a"))

        composeRule.onNodeWithText("example.org").assertIsDisplayed()
    }

    @Test
    fun `loading shows a progress bar that reports its fraction, and it leaves when done`() {
        var state by mutableStateOf(BrowserChromeState(url = "https://example.org", progress = 0.4f))
        composeRule.setContent { NextTheme { BrowserScreen(state, actions()) { Box(it) } } }

        composeRule
            .onNodeWithTag(BrowserTags.PROGRESS)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(
                        0.4f,
                        0f..1f,
                    ),
                ),
            )

        state = state.copy(progress = null)
        composeRule.onNodeWithTag(BrowserTags.PROGRESS).assertDoesNotExist()
    }

    @Test
    fun `back closes the browser and reload reloads the page`() {
        show(BrowserChromeState(url = "https://example.org"))

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithContentDescription("Reload").performClick()

        assertEquals(listOf("close", "reload"), calls)
    }

    @Test
    fun `page history items follow what the page can do`() {
        show(BrowserChromeState(url = "https://example.org", canGoBack = true, canGoForward = false))

        composeRule.onNodeWithTag(ReaderAppBarTags.OVERFLOW).performClick()
        composeRule.onNodeWithText("Next page").assertIsNotEnabled()
        composeRule.onNodeWithText("Previous page").assertIsEnabled().performClick()

        assertEquals(listOf("back"), calls)
    }

    @Test
    fun `share and open in browser are offered when the host supports them`() {
        show(BrowserChromeState(url = "https://example.org"))

        composeRule.onNodeWithTag(ReaderAppBarTags.OVERFLOW).performClick()
        composeRule.onNodeWithText("Open in browser").performClick()
        composeRule.onNodeWithTag(ReaderAppBarTags.OVERFLOW).performClick()
        composeRule.onNodeWithText("Share link").performClick()

        assertEquals(listOf("external", "share"), calls)
    }

    @Test
    fun `the page fills the frame below the bar`() {
        show(BrowserChromeState(url = "https://example.org"))

        composeRule.onNodeWithTag(BrowserTags.PAGE).assertIsDisplayed()
    }

    @Test
    fun `the displayed host drops scheme, credentials, port, path and case`() {
        assertEquals("example.org", browserHost("https://example.org"))
        assertEquals("example.org", browserHost("https://EXAMPLE.org:8443/path?q=1#x"))
        // A credential prefix is how a link pretends to be another site.
        assertEquals("evil.test", browserHost("https://bank.example@evil.test/login"))
        assertEquals("example.org", browserHost("https://example.org?q=1"))
        assertEquals("", browserHost(""))
        assertEquals("", browserHost("not a url"))
    }

    private fun show(state: BrowserChromeState) {
        composeRule.setContent { NextTheme { BrowserScreen(state, actions()) { Box(it) } } }
    }

    private fun actions() =
        BrowserActions(
            onClose = { calls += "close" },
            onReload = { calls += "reload" },
            onBack = { calls += "back" },
            onForward = { calls += "forward" },
            onShare = { calls += "share" },
            onOpenExternal = { calls += "external" },
        )
}
