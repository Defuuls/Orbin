package com.orbin.core.reader

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.orbin.core.model.BoardId
import com.orbin.core.model.InlineStyle
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import com.orbin.core.model.PosterInfo
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ThreadScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/** Real model-to-prepared-body coverage complements ui-next's plain layout/window fixtures. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w390dp-h844dp-mdpi")
class ReaderRichThreadScreenshotTest(
    private val theme: String,
) {
    @get:Rule
    val composeRule = createComposeRule()

    private var savedTimeZone: TimeZone? = null

    @Before
    fun pinUtcTimeZone() {
        savedTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() {
        savedTimeZone?.let(TimeZone::setDefault)
    }

    @Test
    fun productionRichThread() {
        val presentation = runBlocking { thread().preparePresentation(nowMillis = UPDATED_AT) }
        composeRule.setContent {
            NextTheme(darkTheme = theme != "light", amoled = theme == "amoled") {
                ThreadScreen(
                    subject = presentation.thread.subject.orEmpty(),
                    board = "/g/",
                    siteName = "fourchan",
                    posts = presentation.rowModels,
                    body = { post -> presentation.rowsById[post.id]?.let { ReaderPostComment(it) } },
                    statusText = readerThreadStatus(presentation),
                    onBack = {},
                    onReload = {},
                    onWatch = {},
                    onShare = {},
                    onPostReference = {},
                    watching = true,
                    firstUnreadPostId = "102",
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/androidHostTest/screenshots/reader_rich_thread_$theme.png")
    }

    private fun thread(): Thread {
        val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(100))
        val opening =
            Post(
                id = PostId(100),
                board = key.board,
                threadId = key.thread,
                isOriginalPost = true,
                subject = "Shared rich text reader",
                poster = PosterInfo(name = "Anonymous"),
                createdAtMillis = CREATED_AT,
                comment = openingComment(),
                repliesTo = persistentListOf(PostId(101)),
            )
        val reply =
            Post(
                id = PostId(101),
                board = key.board,
                threadId = key.thread,
                isOriginalPost = false,
                poster = PosterInfo(name = "Reader", posterId = "abc"),
                createdAtMillis = CREATED_AT + 60_000,
                comment = replyComment(),
                repliesTo = persistentListOf(PostId(100)),
            )
        val last =
            reply.copy(
                id = PostId(102),
                poster = PosterInfo(name = "Another reader"),
                createdAtMillis = UPDATED_AT,
                comment = PostComment("", persistentListOf(PostNode.Text("A new reply completes the shared thread."))),
                repliesTo = persistentListOf(PostId(100), PostId(101)),
            )
        return Thread(
            key,
            opening,
            persistentListOf(reply, last),
            ThreadStats(replyCount = 2, lastModifiedMillis = UPDATED_AT),
        )
    }

    private fun openingComment(): PostComment =
        PostComment(
            "",
            persistentListOf(
                PostNode.Styled(
                    InlineStyle.GREENTEXT,
                    persistentListOf(PostNode.Text(">A quoted line stays green.")),
                ),
                PostNode.LineBreak,
                PostNode.Text("Read "),
                PostNode.Link("https://example.org/source", persistentListOf(PostNode.Text("the source"))),
                PostNode.Text(" and jump to "),
                PostNode.QuoteLink(PostId(101)),
                PostNode.Text("."),
                PostNode.LineBreak,
                PostNode.Styled(InlineStyle.BOLD, persistentListOf(PostNode.Text("A bold detail. "))),
                PostNode.Styled(
                    InlineStyle.SPOILER,
                    persistentListOf(
                        PostNode.Text("Hidden spoiler text "),
                        PostNode.QuoteLink(PostId(102)),
                    ),
                ),
            ),
        )

    private fun replyComment(): PostComment =
        PostComment(
            "",
            persistentListOf(
                PostNode.QuoteLink(PostId(100)),
                PostNode.Text(" Reference links keep their syntax and underline."),
                PostNode.LineBreak,
                PostNode.Styled(
                    InlineStyle.ITALIC,
                    persistentListOf(PostNode.Text("Selection keeps the original text.")),
                ),
            ),
        )

    companion object {
        private const val CREATED_AT = 1_794_398_520_000L
        private const val UPDATED_AT = CREATED_AT + 120_000L

        @JvmStatic
        @Parameters(name = "{0}")
        fun themes(): List<Array<String>> = listOf("light", "dark", "amoled").map { arrayOf(it) }
    }
}
