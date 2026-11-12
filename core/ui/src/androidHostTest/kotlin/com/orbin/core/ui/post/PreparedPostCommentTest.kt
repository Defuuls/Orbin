package com.orbin.core.ui.post

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.InlineStyle
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class PreparedPostCommentTest {
    private val colors = listOf(CommentColors(Color.Green, Color.Blue, Color.Gray))

    @Test
    fun preparedTextKeepsRichStylesQuotesLinkificationAndVisibleDestinations() {
        val comment =
            PostComment(
                "",
                persistentListOf(
                    PostNode.Styled(InlineStyle.BOLD, persistentListOf(PostNode.Text("bold "))),
                    PostNode.QuoteLink(PostId(12)),
                    PostNode.Text(" www.example.com "),
                    PostNode.Link("https://actual.example", persistentListOf(PostNode.Text("claimed.example"))),
                ),
            )
        val prepared = preparePostComment(comment, colors)
        val text = prepared.texts.single()

        assertThat(text.text).isEqualTo("bold >>12 www.example.com claimed.example\u00A0(actual.example)")
        assertThat(text.getLinkAnnotations(0, text.length)).hasSize(3)
        assertThat(text.spanStyles).isNotEmpty()
    }

    @Test
    fun preparedSpoilerRevealsItsLinksWithoutChangingOtherSpoilers() {
        val spoiler = PostNode.Styled(InlineStyle.SPOILER, persistentListOf(PostNode.QuoteLink(PostId(12))))
        val prepared =
            preparePostComment(PostComment("", persistentListOf(spoiler, PostNode.Text(" "), spoiler)), colors)
        val hidden = prepared.texts.single()
        val revealed = prepared.render(0, setOf(0))

        assertThat(hidden.text).isEqualTo(revealed.text)
        assertThat(
            hidden.spanStyles
                .last()
                .item.color,
        ).isEqualTo(colors.single().spoiler)
        assertThat(
            hidden.spanStyles
                .last()
                .item.background,
        ).isEqualTo(colors.single().spoiler)
        assertThat(
            hidden.getLinkAnnotations(0, hidden.length).map { (it.item as LinkAnnotation.Clickable).tag },
        ).containsExactly("spoiler", "spoiler")
        assertThat(
            revealed.getLinkAnnotations(0, revealed.length).map { (it.item as LinkAnnotation.Clickable).tag },
        ).containsExactly("link", "spoiler")
    }

    @Test
    fun savedPlainTextWithoutNodesIsPreparedAsVisibleText() {
        val prepared = preparePostComment(PostComment(" saved text www.example.com ", persistentListOf()), colors)

        assertThat(prepared.texts.single().text).isEqualTo("saved text www.example.com")
        assertThat(prepared.texts.single().getLinkAnnotations(0, prepared.texts.single().length)).hasSize(1)
    }
}
