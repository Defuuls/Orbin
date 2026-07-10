package com.orbin.core.model

/** Result of checking whether a file-host link still exists. */
enum class LinkStatus {
    /** Not checked, not checkable, or the check could not tell (network error, rate limit). */
    UNKNOWN,

    /** The host confirmed the content exists. */
    VALID,

    /** The host confirmed the content is gone. */
    INVALID,
}

/** The file hosts whose links Orbin can verify. */
object VerifiableLinkHosts {
    private val hosts =
        setOf(
            "gofile.io",
            "fast-file.ru",
            "mega.nz",
            "mega.co.nz",
        )

    /** True when [url] points at a supported file host (including subdomains like `www.`). */
    fun isSupported(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return hosts.any { host == it || host.endsWith(".$it") }
    }

    private fun hostOf(url: String): String? {
        val schemeless = url.substringAfter("://", url)
        val host = schemeless.takeWhile { it != '/' && it != '?' && it != '#' }.substringBefore(':')
        return host.lowercase().ifBlank { null }
    }
}
