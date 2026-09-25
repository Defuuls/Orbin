package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
        capture("next_feed") {
            FeedScreen(
                rows = feedRows(),
                subtitle = SAMPLE_SUBTITLE,
                onOpenBoards = {},
                onOpenMedia = {},
                onSettings = {},
            )
        }

    @Test
    fun feedDark() =
        capture("next_feed_dark", dark = true) {
            FeedScreen(
                rows = feedRows(),
                subtitle = SAMPLE_SUBTITLE,
                onOpenBoards = {},
                onOpenMedia = {},
                onSettings = {},
            )
        }

    @Test
    fun feedAmoled() =
        capture("next_feed_amoled", dark = true, amoled = true) {
            FeedScreen(
                rows = feedRows(),
                subtitle = SAMPLE_SUBTITLE,
                onOpenBoards = {},
                onOpenMedia = {},
                onSettings = {},
            )
        }

    @Test
    fun feedLargeText() =
        capture("next_feed_large_text", fontScale = XL_FONT_SCALE) {
            FeedScreen(
                rows = feedRows(),
                subtitle = SAMPLE_SUBTITLE,
                onOpenBoards = {},
                onOpenMedia = {},
                onSettings = {},
            )
        }

    @Test
    fun feedMaxText() =
        capture("next_feed_max_text", fontScale = MAX_FONT_SCALE) {
            FeedScreen(
                rows = feedRows(),
                subtitle = SAMPLE_SUBTITLE,
                onOpenBoards = {},
                onOpenMedia = {},
                onSettings = {},
            )
        }

    @Test
    fun feedInShell() =
        capture("next_feed_in_shell") {
            FeedScreen(rows = feedRows(), subtitle = SAMPLE_SUBTITLE, showRail = false)
        }

    @Test
    fun feedGrid() =
        capture("next_feed_grid") {
            FeedScreen(
                rows = feedRows(),
                subtitle = SAMPLE_SUBTITLE,
                onOpenBoards = {},
                onOpenMedia = {},
                onSettings = {},
            )
        }

    @Test
    fun board() =
        capture("next_board") {
            val rows = boardRows()
            BoardScreen(
                board = "/g/",
                description = "Technology",
                itemCount = rows.size,
                rowAt = { index -> rows.getOrNull(index) },
            )
        }

    @Test
    fun boardLoading() =
        capture("next_board_loading") {
            MessageScreen(
                title = "/g/",
                subtitle = "Loading…",
                where = "/g/",
                skeleton = true,
            )
        }

    @Test
    fun boardError() =
        capture("next_board_error") {
            MessageScreen(
                title = "/g/",
                subtitle = "Couldn't load this board's catalog",
                actionLabel = "Try again",
                onAction = {},
                where = "/g/",
            )
        }

    @Test
    fun boardEmpty() =
        capture("next_board_empty") {
            MessageScreen(
                title = "/g/",
                subtitle = "Nothing on this board right now.",
                actionLabel = "Refresh",
                onAction = {},
                where = "/g/",
            )
        }

    @Test
    fun boardsBrowse() =
        capture("next_boards") {
            BoardsScreen(
                boards =
                    listOf(
                        BoardTile("g", "/g/", "Technology"),
                        BoardTile("ck", "/ck/", "Food & Cooking"),
                        BoardTile("p", "/p/", "Photography"),
                        BoardTile("lit", "/lit/", "Literature"),
                        BoardTile("a", "/a/", "Anime & Manga", nsfw = false),
                        BoardTile("wg", "/wg/", "Wallpapers/General"),
                    ),
                onOpenFeed = {},
                onOpenMedia = {},
                onOpenSettings = {},
            )
        }

    @Test
    fun thread() =
        capture("next_thread") {
            ThreadScreen(
                subject = "Anyone else running a home server on ARM?",
                board = "/g/",
                posts = posts(),
                watching = true,
            )
        }

    @Test
    fun threadDark() =
        capture("next_thread_dark", dark = true) {
            ThreadScreen(
                subject = "Anyone else running a home server on ARM?",
                board = "/g/",
                posts = posts(),
                watching = true,
            )
        }

    @Test
    fun threadMaxText() =
        capture("next_thread_max_text", fontScale = MAX_FONT_SCALE) {
            ThreadScreen(
                subject = "Anyone else running a home server on ARM?",
                board = "/g/",
                posts = posts(),
                watching = true,
            )
        }

    @Test
    fun threadCollapsed() =
        capture("next_thread_collapsed") {
            ThreadScreen(
                subject = "Anyone else running a home server on ARM?",
                board = "/g/",
                posts = posts(),
                watching = true,
                collapsed = setOf("No.4471028", "No.4471040"),
            )
        }

    @Test
    fun threadFiles() =
        capture("next_thread_files") {
            ThreadScreen(
                subject = "Anyone else running a home server on ARM?",
                board = "/g/",
                posts = posts(),
                watching = true,
                layout = ThreadLayout.FILES,
                files = mediaCells().take(7),
            )
        }

    /** The list at rest: every row closed. */
    @Test
    fun settings() =
        capture("next_settings") {
            SettingsScreen(
                groups = settingsGroups(),
            )
        }

    @Test
    fun settingsMaxText() =
        capture("next_settings_max_text", fontScale = MAX_FONT_SCALE) {
            SettingsScreen(
                groups = settingsGroups(),
            )
        }

    /**
     * A row being changed where it stands, which is the only way this list edits anything: the
     * choice opens its options underneath rather than opening a screen over them.
     */
    @Test
    fun settingsEditing() =
        capture("next_settings_editing") {
            SettingsScreen(
                groups = settingsGroups(),
                expandedId = "themeMode",
            )
        }

    @Test
    fun settingsEditingMaxText() =
        capture("next_settings_editing_max_text", fontScale = MAX_FONT_SCALE) {
            SettingsScreen(
                groups = settingsGroups(),
                expandedId = "themeMode",
            )
        }

    /** Keeps [SettingTextEditor] covered; see [textEditorGroup]. */
    @Test
    fun settingsTextEditor() =
        capture("next_settings_text_editor") {
            SettingsScreen(
                groups = textEditorGroup(),
                expandedId = "userAgent",
            )
        }

    @Test
    fun mediaWall() =
        capture("next_media") {
            MediaWallScreen(
                scanned = 42,
                total = 70,
                failed = 3,
                scanning = true,
                cells = mediaCells(),
                onOpenFeed = {},
                onOpenBoards = {},
                onOpenSettings = {},
            )
        }

    @Test
    fun mediaWallMaxText() =
        capture("next_media_max_text", fontScale = MAX_FONT_SCALE) {
            MediaWallScreen(
                scanned = 42,
                total = 70,
                failed = 3,
                scanning = true,
                cells = mediaCells(),
                onOpenFeed = {},
                onOpenBoards = {},
                onOpenSettings = {},
            )
        }

    private fun capture(
        name: String,
        dark: Boolean = false,
        amoled: Boolean = false,
        fontScale: Float = 1f,
        palette: NextPalette? = null,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            NextTheme(darkTheme = dark, amoled = amoled, fontScale = fontScale, palette = palette) {
                Surface(modifier = Modifier.size(411.dp, 891.dp)) {
                    Box { content() }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/androidHostTest/screenshots/$name.png")
    }

    private companion object {
        const val XL_FONT_SCALE = 1.2f
        const val MAX_FONT_SCALE = 2.0f
        const val SAMPLE_SUBTITLE = "8 threads across 7 boards"
    }

    private fun feedRows() =
        listOf(
            FeedRow("Anyone else running a home server on ARM?", "/g/", "4m", 218, 31, id = "/g/8834912"),
            FeedRow("Weekly desktop thread", "/g/", "12m", 94, 88, id = "/g/8834710"),
            FeedRow("What did you cook this week", "/ck/", "31m", 47, 22, read = true, id = "/ck/1482019"),
            FeedRow("Film photography general — grain edition", "/p/", "1h", 156, 140, id = "/p/4091823"),
            FeedRow("Old ThinkPads that still earn their keep", "/g/", "2h", 63, 19, id = "/g/8833910"),
            FeedRow(
                "Reading list for winter",
                "/lit/",
                "3h",
                28,
                4,
                hasPreview = false,
                read = true,
                id = "/lit/2219481",
            ),
            FeedRow("Post your desk setup", "/g/", "4h", 311, 205, id = "/g/8832104"),
            FeedRow("Cheap mechanical keyboards worth having", "/g/", "6h", 88, 17, id = "/g/8831940"),
        )

    private fun boardRows() =
        listOf(
            FeedRow("Anyone else running a home server on ARM?", "/g/", "4m", 218, 31, id = "/g/8834912"),
            FeedRow("Weekly desktop thread", "/g/", "12m", 94, 88, id = "/g/8834710"),
            FeedRow("Old ThinkPads that still earn their keep", "/g/", "31m", 63, 19, id = "/g/8833910"),
            FeedRow("Post your desk setup", "/g/", "1h", 311, 205, read = true, id = "/g/8832104"),
            FeedRow("Cheap mechanical keyboards worth having", "/g/", "2h", 88, 17, id = "/g/8831940"),
            FeedRow("Self-hosting what you actually use", "/g/", "3h", 41, 6, hasPreview = false, id = "/g/8830155"),
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

    /** Mirrors the real list: two untitled cards, preferences then data. */
    private fun settingsGroups() =
        listOf(
            "" to
                listOf(
                    SettingItem("hideNsfw", "Hide NSFW boards", "Off", SettingKind.TOGGLE),
                    SettingItem(
                        id = "themeMode",
                        label = "Theme",
                        value = "System",
                        kind = SettingKind.CHOICE,
                        options = listOf("System", "Light", "Dark"),
                        selected = 0,
                    ),
                    SettingItem("amoled", "True black", "Off", SettingKind.TOGGLE),
                    SettingItem("biometric", "App lock", "Off", SettingKind.TOGGLE),
                ),
            "" to
                listOf(
                    SettingItem(
                        id = "clearActivity",
                        label = "Clear local activity",
                        value = "Delete",
                        kind = SettingKind.ACTION,
                        hint = "Deletes browsing history, recent searches and download history on this device.",
                    ),
                    SettingItem("clearImageCache", "Clear image cache", "12 MB · Clear", SettingKind.ACTION),
                    SettingItem("checkUpdates", "Check for updates", "Up to date", SettingKind.ACTION),
                    SettingItem("exportBackup", "Export data", "Save", SettingKind.ACTION),
                    SettingItem("importBackup", "Import data", "Restore", SettingKind.ACTION),
                ),
        )

    /**
     * A text row, which the settings list no longer has one of.
     *
     * [SettingTextEditor] is still live in `:ui-next` and the registry still carries the commit
     * plumbing, so the editor keeps a capture of its own rather than riding on a settings fixture
     * that would have to misrepresent the list to provide it. Deliberately not [settingsGroups]:
     * this is a component under test, not the app's settings surface.
     */
    private fun textEditorGroup() =
        listOf(
            "Text row" to
                listOf(
                    SettingItem(
                        id = "userAgent",
                        label = "Custom user agent",
                        value = "Default",
                        kind = SettingKind.TEXT,
                        text = "Orbin/1.0",
                        hint = "Sent with every request. Leave empty to use Orbin's default.",
                    ),
                ),
        )

    private fun mediaCells() =
        List(15) { index ->
            MediaCell(id = "cell-$index", board = listOf("/g/", "/ck/", "/p/", "/lit/")[index % 4])
        }
}
