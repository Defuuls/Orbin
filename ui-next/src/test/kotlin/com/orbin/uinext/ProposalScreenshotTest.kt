package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
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

/**
 * Renders the proposed interface at real phone size so it can be judged before it is wired to
 * anything. These are screenshots of the actual composables, not mockups — whatever is wrong in
 * the picture is wrong in the code, and can be fixed there.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ProposalScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun feed() =
        capture("next_feed") { FeedScreen(rows = feedRows(), subtitle = SAMPLE_SUBTITLE, railDetail = "7 boards") }

    @Test
    fun feedDark() =
        capture("next_feed_dark", dark = true) {
            FeedScreen(rows = feedRows(), subtitle = SAMPLE_SUBTITLE, railDetail = "7 boards")
        }

    /** What the feed actually looks like as shipped: inside the app shell, which still owns the
     *  bottom navigation bar, so the rail stays off until the command surface replaces that bar. */
    @Test
    fun feedInShell() =
        capture("next_feed_in_shell") {
            FeedScreen(rows = feedRows(), subtitle = SAMPLE_SUBTITLE, showRail = false)
        }

    @Test
    fun board() =
        capture("next_board") {
            BoardScreen(
                board = "/g/",
                description = "Technology",
                rows = boardRows(),
            )
        }

    @Test
    fun thread() =
        capture("next_thread") {
            ThreadScreen(
                subject = "Anyone else running a home server on ARM?",
                board = "/g/",
                posts = posts(),
            )
        }

    @Test
    fun threadDark() =
        capture("next_thread_dark", dark = true) {
            ThreadScreen(
                subject = "Anyone else running a home server on ARM?",
                board = "/g/",
                posts = posts(),
            )
        }

    @Test
    fun command() =
        capture("next_command") {
            CommandSheet(
                query = "auto",
                results =
                    listOf(
                        Command("Autoplay videos", "setting", "Media · currently on"),
                        Command("Autoplay videos in feed", "setting", "Media · currently off"),
                        Command("Auto-rotate video", "setting", "Media · currently on"),
                        Command("/aco/", "board", "Adult Cartoons · subscribed"),
                        Command("Automotive threads", "search", "12 saved results"),
                        Command("Autumn photo dump", "thread", "/p/ · 84 replies · open"),
                    ),
            )
        }

    @Test
    fun settings() = capture("next_settings") { SettingsScreen(groups = settingsGroups()) }

    @Test
    fun mediaWall() = capture("next_media") { MediaWallScreen(scanned = 42, total = 70, failed = 3) }

    private fun capture(
        name: String,
        dark: Boolean = false,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        composeRule.setContent {
            NextTheme(darkTheme = dark) {
                Surface(modifier = Modifier.size(411.dp, 891.dp)) {
                    Box { content() }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        const val SAMPLE_SUBTITLE = "8 threads across 7 boards"
    }

    private fun feedRows() =
        listOf(
            FeedRow("Anyone else running a home server on ARM?", "/g/", "4m", 218, 31),
            FeedRow("Weekly desktop thread", "/g/", "12m", 94, 88),
            FeedRow("What did you cook this week", "/ck/", "31m", 47, 22, read = true),
            FeedRow("Film photography general — grain edition", "/p/", "1h", 156, 140),
            FeedRow("Old ThinkPads that still earn their keep", "/g/", "2h", 63, 19),
            FeedRow("Reading list for winter", "/lit/", "3h", 28, 4, hasPreview = false, read = true),
            FeedRow("Post your desk setup", "/g/", "4h", 311, 205),
            FeedRow("Cheap mechanical keyboards worth having", "/g/", "6h", 88, 17),
        )

    private fun boardRows() =
        listOf(
            FeedRow("Anyone else running a home server on ARM?", "/g/", "4m", 218, 31),
            FeedRow("Weekly desktop thread", "/g/", "12m", 94, 88),
            FeedRow("Old ThinkPads that still earn their keep", "/g/", "31m", 63, 19),
            FeedRow("Post your desk setup", "/g/", "1h", 311, 205, read = true),
            FeedRow("Cheap mechanical keyboards worth having", "/g/", "2h", 88, 17),
            FeedRow("Self-hosting what you actually use", "/g/", "3h", 41, 6, hasPreview = false),
        )

    private fun posts() =
        listOf(
            Post(
                "No.4471028",
                "14:02",
                "Been running a small NAS on an ARM board for about eight months now. Idle draw is " +
                    "under four watts and it has not fallen over once.",
                hasMedia = true,
                replies = 6,
            ),
            Post("No.4471033", "14:09", "Which board? The cheap ones tend to have terrible SATA.", depth = 1),
            Post(
                "No.4471040",
                "14:15",
                "Not the one you are thinking of. It has a proper PCIe lane rather than USB behind " +
                    "a bridge, which is the whole difference.",
                depth = 2,
                replies = 2,
            ),
            Post("No.4471051", "14:22", "", depth = 1, spoiler = true),
            Post(
                "No.4471066",
                "14:40",
                "Power figures over a month, if anyone wants them.",
                hasMedia = true,
            ),
        )

    private fun settingsGroups() =
        listOf(
            "Content" to
                listOf(
                    "Built-in content filter" to "Always on",
                    "Hidden tags" to "3",
                    "Hide NSFW boards" to "Off",
                    "Threads per board" to "50",
                ),
            "Appearance" to
                listOf(
                    "Color theme" to "Yotsuba",
                    "Dynamic color" to "Off",
                    "AMOLED black" to "On",
                    "Font size" to "Medium",
                    "App icon" to "Classic",
                ),
            "Media" to
                listOf(
                    "Autoplay videos" to "On",
                    "Autoplay videos in feed" to "Off",
                    "Mute by default" to "On",
                    "Thumbnail size" to "Medium",
                ),
            "Privacy" to
                listOf(
                    "Lock with biometrics" to "On",
                    "DNS over HTTPS" to "Cloudflare",
                    "Custom user agent" to "Default",
                ),
        )
}
