package com.orbin.provider.api

import com.orbin.core.model.BoardId
import com.orbin.core.model.PostId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey

/** A thread identity from a registered site. The pasted URL is never fetched directly. */
data class ThreadLink(
    val key: ThreadKey,
    val post: PostId? = null,
)

/**
 * Resolves only a registered provider's ordinary thread URLs, retaining the provider identity.
 * Legacy HTTP links are accepted as identities; loading still uses the provider's configured
 * transport. Credentials, custom ports, lookalike hosts and ambiguous registrations are rejected.
 */
fun parseThreadLink(
    text: String,
    providers: List<ProviderMetadata>,
): ThreadLink? {
    val value = text.trim()
    val parts = threadLinkTransport(value) ?: return null
    val host = parts.host ?: return null
    val path = parts.path ?: return null
    val anchor = value.substringAfter('#', "").removePrefix("p")
    val post = anchor.toLongOrNull()?.takeIf { it > 0 }?.let(::PostId)
    return providers
        .mapNotNull { provider ->
            val base = UriParts.parse(provider.baseUrl) ?: return@mapNotNull null
            val fourchan = provider.engine == EngineKind.FOURCHAN || base.host?.lowercase() in FOURCHAN_THREAD_HOSTS
            val aliases = fourchan && host.lowercase() in FOURCHAN_THREAD_HOSTS
            if (!host.equals(base.host, true) && !aliases) return@mapNotNull null
            val prefix = base.path.orEmpty().trimEnd('/')
            val relative =
                when {
                    aliases || prefix.isEmpty() -> path
                    path.startsWith("$prefix/") -> path.removePrefix(prefix)
                    else -> return@mapNotNull null
                }
            val match =
                (if (fourchan) FOURCHAN_THREAD_PATH else ENGINE_THREAD_PATH).matchEntire(relative)
                    ?: return@mapNotNull null
            val number = match.groupValues[2].toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val board = match.groupValues[1].let { if (fourchan) it.lowercase() else it }
            ThreadLink(ThreadKey(provider.id, BoardId(board), ThreadId(number)), post)
        }.distinctBy { it.key }
        .singleOrNull()
}

/** Resolve identities only from ordinary HTTP(S) authorities without credentials or ports. */
private fun threadLinkTransport(value: String): UriParts? {
    val parts = UriParts.parse(value) ?: return null
    val supportedScheme = parts.scheme.equals("https", true) || parts.scheme.equals("http", true)
    val authority =
        value
            .substringAfter("://", "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
    val ordinaryAuthority = authority.isNotBlank() && authority.none { it == '@' || it == ':' }
    return parts.takeIf { supportedScheme && ordinaryAuthority }
}

private val FOURCHAN_THREAD_HOSTS = setOf("boards.4chan.org", "boards.4channel.org")
private val FOURCHAN_THREAD_PATH = Regex("^/([A-Za-z0-9_-]{1,64})/thread/([0-9]+)(?:/[^/]*)?/?$")
private val ENGINE_THREAD_PATH = Regex("^/([A-Za-z0-9_-]{1,64})/res/([0-9]+)(?:\\.html)?/?$")
