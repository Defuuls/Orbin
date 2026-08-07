package com.orbin.core.ui.post

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.google.common.truth.Truth.assertThat
import com.orbin.core.designsystem.theme.GreentextColor
import com.orbin.core.designsystem.theme.SpoilerBackground
import com.orbin.core.model.InlineStyle
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

/**
 * Covers the spoiler rendering contract of [PostCommentText] through [buildCommentText].
 *
 * These assertions read the produced [AnnotatedString] rather than comparing pixels, because what
 * matters is structural: which ranges are blacked out, and which ranges are tappable. A screenshot
 * would confirm the first and say nothing about the second.
 */
class PostCommentSpoilerTest {
    private val revealedBackground = Color(0xFF445566)

    private fun comment(vararg nodes: PostNode): PostComment = PostComment(raw = "", nodes = persistentListOf(*nodes))

    private fun spoiler(vararg children: PostNode): PostNode =
        PostNode.Styled(InlineStyle.SPOILER, persistentListOf(*children))

    private fun render(
        comment: PostComment,
        revealed: Set<Int> = emptySet(),
        interactive: Boolean = true,
    ): AnnotatedString =
        buildCommentText(
            comment = comment,
            ctx =
                CommentRenderContext(
                    revealedSpoilers = revealed,
                    revealedSpoilerBackground = revealedBackground,
                ),
            interactive = interactive,
        )

    /** The blacked-out span paints the glyphs in the background colour, so nothing is legible. */
    private fun AnnotatedString.blackedOutRanges() =
        spanStyles.filter { it.item.background == SpoilerBackground && it.item.color == SpoilerBackground }

    private fun AnnotatedString.links() = getLinkAnnotations(0, length)

    @Test
    fun aSpoilerStartsBlackedOutOverExactlyItsOwnRange() {
        val text = render(comment(PostNode.Text(PREFIX), spoiler(PostNode.Text(SECRET))))

        assertThat(text.text).isEqualTo(PREFIX + SECRET)
        val blackedOut = text.blackedOutRanges()
        assertThat(blackedOut).hasSize(1)
        // The visible prefix must not be swept up in the blackout.
        assertThat(blackedOut.single().start).isEqualTo(PREFIX.length)
        assertThat(blackedOut.single().end).isEqualTo(PREFIX.length + SECRET.length)
    }

    @Test
    fun aHiddenSpoilerIsTappableSoItCanBeRevealed() {
        val text = render(comment(PostNode.Text(PREFIX), spoiler(PostNode.Text(SECRET))))

        assertThat(text.links()).hasSize(1)
        assertThat(text.links().single().start).isEqualTo(PREFIX.length)
    }

    @Test
    fun revealingASpoilerDropsTheBlackoutAndItsRevealLink() {
        val subject = comment(PostNode.Text(PREFIX), spoiler(PostNode.Text(SECRET)))

        val revealed = render(subject, revealed = setOf(0))

        assertThat(revealed.text).isEqualTo(PREFIX + SECRET)
        assertThat(revealed.blackedOutRanges()).isEmpty()
        assertThat(revealed.links()).isEmpty()
        // Still visibly a spoiler, just a readable one.
        assertThat(revealed.spanStyles.map { it.item.background }).contains(revealedBackground)
    }

    /**
     * Ids are positional, so revealing one span must leave its neighbour hidden. A shared or
     * mis-assigned id would reveal both at once.
     */
    @Test
    fun revealingOneSpoilerLeavesItsSiblingHidden() {
        val subject =
            comment(
                spoiler(PostNode.Text("one")),
                PostNode.Text(" and "),
                spoiler(PostNode.Text("two")),
            )

        assertThat(render(subject).blackedOutRanges()).hasSize(2)

        val firstRevealed = render(subject, revealed = setOf(0))
        assertThat(firstRevealed.blackedOutRanges()).hasSize(1)
        // The survivor is the second spoiler, which sits after "one and ".
        assertThat(firstRevealed.blackedOutRanges().single().start).isEqualTo("one and ".length)

        val secondRevealed = render(subject, revealed = setOf(1))
        assertThat(secondRevealed.blackedOutRanges()).hasSize(1)
        assertThat(secondRevealed.blackedOutRanges().single().start).isEqualTo(0)
    }

    /**
     * A quote link inside a hidden spoiler must not be tappable: it sits on top of the reveal
     * target, so the first tap would navigate to a post the reader cannot yet see they were
     * offered. Once revealed it behaves normally again.
     */
    @Test
    fun aQuoteLinkInsideASpoilerIsInertUntilTheSpoilerIsRevealed() {
        val subject =
            comment(
                PostNode.Text(PREFIX),
                spoiler(PostNode.Text("see "), PostNode.QuoteLink(target = PostId(123L))),
            )

        // Hidden: the only link is the spoiler's own reveal target.
        val hidden = render(subject)
        assertThat(hidden.links()).hasSize(1)
        assertThat(hidden.links().single().start).isEqualTo(PREFIX.length)

        // Revealed: the reveal link is gone and the quote link is live in its place.
        val revealed = render(subject, revealed = setOf(0))
        assertThat(revealed.links()).hasSize(1)
        assertThat(revealed.links().single().start).isEqualTo(revealed.text.indexOf(">>123"))
    }

    /**
     * Previews are deliberately inert — taps belong to the enclosing card — so a spoiler there
     * stays blacked out and contributes no link annotation to compete for the tap.
     */
    @Test
    fun previewSpoilersAreHiddenAndNotTappable() {
        val text = render(comment(spoiler(PostNode.Text(SECRET))), interactive = false)

        assertThat(text.blackedOutRanges()).hasSize(1)
        assertThat(text.links()).isEmpty()
    }

    @Test
    fun nonSpoilerStylesAreUnaffected() {
        val text = render(comment(PostNode.Styled(InlineStyle.GREENTEXT, persistentListOf(PostNode.Text(">implying")))))

        assertThat(text.blackedOutRanges()).isEmpty()
        assertThat(text.links()).isEmpty()
        assertThat(text.spanStyles.map { it.item.color }).contains(GreentextColor)
    }

    private companion object {
        const val PREFIX = "plain "
        const val SECRET = "hidden"
    }
}
