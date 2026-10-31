package com.orbin.core.ui.post

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostNode
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

/** Link text that names one address while the link opens another shows the real host. */
class LinkDestinationTest {
    @Test
    fun textNamingAnotherDomainIsFlaggedWithTheRealHost() {
        assertThat(misleadingLinkHost("paypal.com", "https://evil.example/login")).isEqualTo("evil.example")
        assertThat(misleadingLinkHost("https://paypal.com/signin", "https://evil.example")).isEqualTo("evil.example")
        assertThat(misleadingLinkHost("www.bank.com", "http://203.0.113.9/")).isEqualTo("203.0.113.9")
    }

    @Test
    fun theSameHostASubdomainOrWwwIsNotFlagged() {
        assertThat(misleadingLinkHost("example.com", "https://example.com/a")).isNull()
        assertThat(misleadingLinkHost("example.com", "https://www.example.com/a")).isNull()
        assertThat(misleadingLinkHost("www.example.com", "https://example.com")).isNull()
        assertThat(misleadingLinkHost("example.com", "https://docs.example.com")).isNull()
        assertThat(misleadingLinkHost("HTTPS://Example.COM/x", "https://example.com:443/y")).isNull()
    }

    @Test
    fun aParentDomainOrLookalikeSuffixIsStillFlagged() {
        // Only a subdomain of the named host is trusted, not a host that merely ends with its text.
        assertThat(misleadingLinkHost("example.com", "https://notexample.com")).isEqualTo("notexample.com")
        assertThat(misleadingLinkHost("docs.example.com", "https://example.com")).isEqualTo("example.com")
    }

    @Test
    fun userinfoInTheLinkDoesNotHideTheRealHost() {
        assertThat(misleadingLinkHost("paypal.com", "https://paypal.com@evil.example/")).isEqualTo("evil.example")
    }

    @Test
    fun textThatIsNotAnAddressMakesNoClaim() {
        assertThat(misleadingLinkHost("source", "https://evil.example")).isNull()
        assertThat(misleadingLinkHost("click here", "https://evil.example")).isNull()
        assertThat(misleadingLinkHost("", "https://evil.example")).isNull()
        assertThat(misleadingLinkHost("see paypal.com for details", "https://evil.example")).isNull()
    }

    @Test
    fun aLookalikeUnicodeDomainShowsThePunycodeHost() {
        // "pаypal.com" with a Cyrillic а.
        assertThat(misleadingLinkHost("p\u0430ypal.com", "https://xn--pypal-4ve.com")).isEqualTo("xn--pypal-4ve.com")
    }

    @Test
    fun theRendererAppendsTheRealHostOutsideTheLink() {
        val link = PostNode.Link("https://evil.example/x", persistentListOf(PostNode.Text("paypal.com")))
        val rendered = buildCommentText(PostComment("", persistentListOf(link)), CommentRenderContext())

        assertThat(rendered.text).isEqualTo("paypal.com\u00A0(evil.example)")
        val links = rendered.getLinkAnnotations(0, rendered.length)
        assertThat(links).hasSize(1)
        assertThat(links.single().end).isEqualTo("paypal.com".length)
    }

    @Test
    fun anHonestLinkRendersUnchanged() {
        val link = PostNode.Link("https://example.com/x", persistentListOf(PostNode.Text("example.com/x")))
        val rendered = buildCommentText(PostComment("", persistentListOf(link)), CommentRenderContext())

        assertThat(rendered.text).isEqualTo("example.com/x")
    }
}
