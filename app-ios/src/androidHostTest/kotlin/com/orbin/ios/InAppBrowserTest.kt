package com.orbin.ios

import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.orbin.graph.orbinProviders
import com.orbin.uinext.components.NextTheme
import com.orbin.uinext.components.ReaderAppBarTags
import com.orbin.uinext.screens.BrowserChromeState
import com.orbin.uinext.screens.BrowserTags
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class InAppBrowserTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val inApp = mutableListOf<String>()
    private val external = RecordingUriHandler()

    private fun handler(
        openLinksInApp: () -> Boolean,
        inAppAvailable: Boolean = true,
    ) = ReaderUriHandler(openLinksInApp, inAppAvailable, openInApp = { inApp += it }, external = external)

    @Test
    fun linksOpenInTheAppWhenTheSettingIsOnAndTheBrowserExists() {
        handler({ true }).openUri(" https://example.org/a ")
        assertEquals(listOf("https://example.org/a"), inApp)
        assertEquals(emptyList(), external.opened)
    }

    @Test
    fun linksOpenExternallyWhenTheSettingIsOffOrThereIsNoBrowser() {
        handler({ false }).openUri("https://example.org/a")
        handler({ true }, inAppAvailable = false).openUri("https://example.org/b")
        assertEquals(emptyList(), inApp)
        assertEquals(listOf("https://example.org/a", "https://example.org/b"), external.opened)
    }

    @Test
    fun unsafeLinksOpenNothingWhateverTheSetting() {
        for (setting in listOf(true, false)) {
            val links = handler({ setting })
            listOf("javascript:alert(1)", "http://example.org", "file:///etc/hosts", "intent://x#Intent;end")
                .forEach(links::openUri)
        }
        assertEquals(emptyList(), inApp)
        assertEquals(emptyList(), external.opened)
    }

    @Test
    fun theSettingIsReadAtEachTap() {
        var setting = true
        val links = handler({ setting })
        links.openUri("https://example.org/a")
        setting = false
        links.openUri("https://example.org/b")
        assertEquals(listOf("https://example.org/a"), inApp)
        assertEquals(listOf("https://example.org/b"), external.opened)
    }

    @Test
    fun pagesFollowTheLinkRuleAndFramesMayAlsoBeBlankDocuments() {
        assertTrue(allowsInAppBrowserNavigation("https://example.org/next", mainFrame = true))
        assertTrue(allowsInAppBrowserNavigation("https://widgets.example.net/frame", mainFrame = false))
        for (blank in listOf("about:blank", "about:srcdoc", " ABOUT:BLANK ")) {
            assertTrue(allowsInAppBrowserNavigation(blank, mainFrame = false), blank)
            assertFalse(allowsInAppBrowserNavigation(blank, mainFrame = true), blank)
        }
        for (mainFrame in listOf(true, false)) {
            listOf(
                null,
                "",
                "http://example.org",
                "file:///private/var/mobile/x",
                "javascript:alert(1)",
                "data:text/html,hi",
                "itms-apps://apps.apple.com/app/id1",
                "tel:123",
                "about:blank#x",
            ).forEach { url -> assertFalse(allowsInAppBrowserNavigation(url, mainFrame), "$url main=$mainFrame") }
        }
    }

    @Test
    fun theFrameShowsProgressOnlyWhileLoading() {
        val loading = webPageChrome("https://example.org", "Example", loading = true, 0.4, true, false)
        assertEquals(BrowserChromeState("Example", "https://example.org", 0.4f, true, false), loading)
        assertNull(webPageChrome("https://example.org", null, loading = false, 1.0, false, false).progress)
        assertEquals(1f, webPageChrome(null, null, loading = true, 1.7, false, false).progress)
        assertEquals("", webPageChrome(null, null, loading = false, 0.0, false, false).url)
    }

    @Test
    fun aWebPageOpensAboveTheCurrentScreenAndBackReturnsThere() =
        runTest {
            val browser = browser(backgroundScope)
            browser.openWebPage(" https://example.org/a ")
            assertEquals(listOf(Route.Feed, Route.WebPage("https://example.org/a")), browser.backStack.value)
            assertNull(primaryNavigationDestination(browser.backStack.value.last()))
            assertTrue(browser.back())
            assertEquals(listOf(Route.Feed), browser.backStack.value)
        }

    @Test
    fun anUnsafeLinkNeverBecomesAWebPage() =
        runTest {
            val browser = browser(backgroundScope)
            listOf("http://example.org", "javascript:alert(1)", "").forEach(browser::openWebPage)
            assertEquals(listOf(Route.Feed), browser.backStack.value)
        }

    @Test
    fun theScreenClosesShowsTheSiteAndOpensThePageExternally() =
        runTest {
            val browser = browser(backgroundScope)
            browser.openWebPage("https://example.org/a")
            val route = browser.backStack.value.last() as Route.WebPage
            composeRule.setContent { NextTheme { WebPageDestination(browser, route, external) } }

            composeRule.onNodeWithTag(BrowserTags.PAGE).assertExists()
            composeRule.onNodeWithText("example.org").assertExists()
            composeRule.onNodeWithTag(ReaderAppBarTags.OVERFLOW).performClick()
            composeRule.onNodeWithText("Open in browser").performClick()
            assertEquals(listOf("https://example.org/a"), external.opened)

            composeRule.onNodeWithContentDescription("Back").performClick()
            composeRule.runOnIdle { assertEquals(listOf(Route.Feed), browser.backStack.value) }
        }

    private fun browser(scope: CoroutineScope): Browser {
        val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
        return Browser(
            orbinProviders(iosHttpClient(engine)),
            FakeBookmarks(),
            FakeHistory(),
            FakeBoardPreferences(),
            FakeSettings(),
            scope,
        )
    }

    class RecordingUriHandler : UriHandler {
        val opened = mutableListOf<String>()

        override fun openUri(uri: String) {
            opened += uri
        }
    }
}
