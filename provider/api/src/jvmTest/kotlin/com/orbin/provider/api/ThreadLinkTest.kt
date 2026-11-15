package com.orbin.provider.api

import com.orbin.core.model.BoardId
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadLinkTest {
    private val fourchan =
        ProviderMetadata(ProviderId("fourchan"), "4chan", "https://boards.4chan.org", engine = EngineKind.VICHAN)
    private val other =
        ProviderMetadata(ProviderId("bbwchan"), "BBWChan", "https://bbw-chan.link", engine = EngineKind.LYNXCHAN)
    private val sites = listOf(fourchan, other)

    @Test
    fun canonicalLinksKeepTheirProviderBoardThreadAndPost() {
        assertEquals(
            ThreadLink(ThreadKey(fourchan.id, BoardId("g"), ThreadId(123)), PostId(456)),
            parseThreadLink("  https://boards.4chan.org/g/thread/123/example#p456  ", sites),
        )
        assertEquals(
            ThreadLink(ThreadKey(other.id, BoardId("g"), ThreadId(123)), PostId(456)),
            parseThreadLink("https://bbw-chan.link/g/res/123.html#456", sites),
        )
    }

    @Test
    fun legacyAndAliasLinksResolveToConfiguredTransportIdentity() {
        assertEquals(fourchan.id, parseThreadLink("http://BOARDS.4CHANNEL.ORG/G/thread/123", sites)?.key?.provider)
        assertEquals(BoardId("g"), parseThreadLink("http://BOARDS.4CHANNEL.ORG/G/thread/123", sites)?.key?.board)
        assertEquals(
            ThreadId(123),
            parseThreadLink("https://bbw-chan.link/g/res/123?anything=else", sites)?.key?.thread,
        )
    }

    @Test
    fun onlyRegisteredSitesAndExactThreadPathsAreAccepted() {
        listOf(
            "https://boards.4chan.org.evil.example/g/thread/123",
            "https://boards.4chan.org@evil.example/g/thread/123",
            "https://someone@boards.4chan.org/g/thread/123",
            "https://boards.4chan.org:8443/g/thread/123",
            "javascript:https://boards.4chan.org/g/thread/123",
            "https://boards.4chan.org/g/",
            "https://boards.4chan.org/g/thread/0",
            "https://boards.4chan.org/g/thread/-1",
            "https://boards.4chan.org/g/thread/9223372036854775808",
            "https://boards.4chan.org/g/thread/123/../../evil",
            "/g/thread/123",
            "https://boards.4chan.org/g%2Fother/thread/123",
        ).forEach { assertNull(it, parseThreadLink(it, sites)) }
        assertNull(parseThreadLink("https://boards.4chan.org/g/thread/123", listOf(other)))
    }

    @Test
    fun aConfiguredBasePathAndAmbiguousHostRegistrationsAreHandled() {
        val nested = other.copy(baseUrl = "https://board.example/chan")
        assertEquals(
            other.id,
            parseThreadLink("https://board.example/chan/g/res/12.html", listOf(nested))?.key?.provider,
        )
        assertNull(parseThreadLink("https://board.example/g/res/12.html", listOf(nested)))
        assertNull(
            parseThreadLink(
                "https://bbw-chan.link/g/res/12.html",
                listOf(other, other.copy(id = ProviderId("second"))),
            ),
        )
    }
}
