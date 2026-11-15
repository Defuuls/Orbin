package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Complete primary-page shells, including the actual adaptive navigation reservation. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w960dp-h720dp-mdpi")
class OrbinAdaptiveNavigationScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun wideFeedWithLeftNavigation() =
        capture("navigation_wide_feed_light", 960.dp, 720.dp, NextDestination.FEED) { ShellFeed() }

    @Test
    fun wideBoardsWithLeftNavigation() =
        capture("navigation_wide_boards_dark", 960.dp, 720.dp, NextDestination.BOARDS, dark = true) {
            BoardsScreen(
                boards =
                    listOf(
                        BoardTile("fourchan/g", "/g/", "Technology", followed = true, site = "4chan"),
                        BoardTile("fourchan/p", "/p/", "Photography", followed = true, site = "4chan"),
                        BoardTile("fourchan/ck", "/ck/", "Food and cooking", site = "4chan"),
                        BoardTile("bbwchan/gen", "/gen/", "General discussion", site = "BBWChan"),
                    ),
                subtitle = "Browse your sources and followed boards",
                onOpenBoard = {},
                onFollowBoard = { _, _ -> },
                onOpenFeed = {},
                onOpenDownloads = {},
                onOpenSettings = {},
                onOpenSearch = {},
                onOpenSaved = {},
                onOpenWatched = {},
                hideRailOnScroll = false,
            )
        }

    @Test
    @Config(qualifiers = "en-rUS-w390dp-h844dp-mdpi")
    fun phoneFeedWithBottomNavigation() =
        capture("navigation_phone_feed_light", 390.dp, 844.dp, NextDestination.FEED) { ShellFeed() }

    private fun capture(
        name: String,
        width: Dp,
        height: Dp,
        selected: NextDestination,
        dark: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            NextTheme(darkTheme = dark, platform = NextPlatform.IOS) {
                Surface(Modifier.size(width, height), color = reader.background) {
                    OrbinAdaptiveNavigation(
                        enabled = true,
                        navigation = { OrbinSideBar(selected = selected, onSelect = {}, downloadBadge = 2) },
                        content = content,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/$name.png")
    }
}

@Composable
private fun ShellFeed() {
    FeedScreen(
        state =
            FeedScreenState(
                rows =
                    listOf(
                        FeedRow(
                            "A quiet landscape",
                            "/p/",
                            "5m",
                            24,
                            8,
                            id = "fourchan/p/1042",
                            site = "4chan",
                            poster = "Anonymous",
                            excerpt = "Share a favorite view from a recent walk.",
                            mediaLabel = "JPG",
                        ),
                        FeedRow(
                            "This week's projects",
                            "/g/",
                            "12m",
                            48,
                            3,
                            id = "fourchan/g/2084",
                            site = "4chan",
                            excerpt = "What are you working on this week?",
                        ),
                    ),
                sourceLabels = listOf("All sites", "4chan", "BBWChan"),
                mode = CatalogMode.CARD,
                downloadBadge = 2,
            ),
        actions =
            FeedScreenActions(
                onSelectSource = {},
                onOpenRow = {},
                onOpenBoards = {},
                onOpenDownloads = {},
                onSettings = {},
                onOpenSearch = {},
                onOpenSaved = {},
                onOpenWatched = {},
                onToggleWatch = {},
                onModeChange = {},
            ),
        thumbnail = { _, modifier -> Box(modifier.background(reader.selected)) },
    )
}
