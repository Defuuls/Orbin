package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Deterministic state fixtures, also reusable by the full reader window/font/direction matrix. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ReaderStatesScreenshotTest(
    private val theme: String,
    private val state: ReaderStateFixture,
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun state() {
        composeRule.setContent {
            NextTheme(darkTheme = theme != "light", amoled = theme == "amoled") {
                ReaderStateScreenshotFixture(state)
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            "src/androidHostTest/screenshots/reader_state_${theme}_${state.name.lowercase()}.png",
        )
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}-{1}")
        fun cases(): List<Array<Any>> =
            listOf("light", "dark", "amoled").flatMap { theme ->
                ReaderStateFixture.entries.map { state -> arrayOf(theme, state) }
            }
    }
}

enum class ReaderStateFixture {
    CATALOG_LOADING,
    GRID_LOADING,
    THREAD_LOADING,
    EMPTY,
    NETWORK_ERROR,
    NOT_FOUND,
    SERVER_ERROR,
    REFRESHING,
    REFRESH_FAILED,
}

@Composable
fun ReaderStateScreenshotFixture(
    state: ReaderStateFixture,
    modifier: Modifier = Modifier,
    wideWindow: Boolean = false,
) {
    Box(modifier.fillMaxSize()) {
        if (state == ReaderStateFixture.REFRESHING || state == ReaderStateFixture.REFRESH_FAILED) {
            BoardScreen(
                board = "/g/",
                description = "Technology",
                itemCount = 1,
                rowAt = {
                    FeedRow(
                        subject = "A thread that stays visible during refresh",
                        board = "/g/",
                        activity = "2m",
                        replies = 23,
                        media = 4,
                        excerpt = "The loaded catalog remains available.",
                        hasPreview = false,
                        id = "fourchan/g/1",
                    )
                },
                reloading = state == ReaderStateFixture.REFRESHING,
                wideWindow = wideWindow,
            )
            if (state == ReaderStateFixture.REFRESH_FAILED) {
                ReaderRefreshFailure(
                    cause = ReaderFailureCause.NETWORK,
                    onRetry = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ReaderAppBar(title = "Technology")
                when (state) {
                    ReaderStateFixture.CATALOG_LOADING -> ReaderCatalogSkeleton(wideWindow = wideWindow)
                    ReaderStateFixture.GRID_LOADING ->
                        ReaderCatalogSkeleton(
                            mode = CatalogMode.GRID,
                            wideWindow = wideWindow,
                        )
                    ReaderStateFixture.THREAD_LOADING -> ReaderThreadSkeleton(wideWindow = wideWindow)
                    ReaderStateFixture.EMPTY ->
                        ReaderEmptyState(
                            "No threads",
                            "Reload to check for new threads.",
                            onReload = {},
                        )
                    ReaderStateFixture.NETWORK_ERROR -> ReaderErrorState(ReaderFailureCause.NETWORK, onRetry = {})
                    ReaderStateFixture.NOT_FOUND -> ReaderErrorState(ReaderFailureCause.NOT_FOUND, onRetry = {})
                    ReaderStateFixture.SERVER_ERROR -> ReaderErrorState(ReaderFailureCause.SERVER, onRetry = {})
                    ReaderStateFixture.REFRESHING, ReaderStateFixture.REFRESH_FAILED -> Unit
                }
            }
        }
    }
}
