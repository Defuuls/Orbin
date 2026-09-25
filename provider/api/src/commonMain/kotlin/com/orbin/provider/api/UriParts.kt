package com.orbin.provider.api

/**
 * The parts of a URI the providers' sanitizers read: scheme, host and decoded path.
 *
 * A multiplatform stand-in for `java.net.URI`, which iOS does not have. [parse] returns null
 * wherever `java.net.URI` would throw, and [host] is null wherever `URI.getHost()` would be (for
 * example a registry-based authority such as `my_host`). `UriPartsTest` checks both against
 * `java.net.URI` on the JVM, so the sanitizers built on it behave identically on every platform.
 */
data class UriParts(
    val scheme: String?,
    val host: String?,
    val path: String?,
) {
    companion object {
        /** Parses [value] with `java.net.URI`'s rules, or returns null where it would reject it. */
        fun parse(value: String): UriParts? {
            if (!isWellFormed(value)) return null
            val beforeFragment = value.substringBefore('#')
            val scheme = schemePrefix(beforeFragment)
            val rest = if (scheme != null) beforeFragment.substring(scheme.length + 1) else beforeFragment
            // Opaque, e.g. mailto:x — no authority, no path.
            val isOpaque = scheme != null && !rest.startsWith('/')
            return if (isOpaque) {
                UriParts(
                    scheme,
                    host = null,
                    path = null,
                )
            } else {
                parseHierarchical(scheme, rest.substringBefore('?'))
            }
        }

        private fun isWellFormed(value: String): Boolean {
            val beforeFragment = value.substringBefore('#')
            val singleFragment = '#' !in value.substringAfter('#', missingDelimiterValue = "")
            val scheme = schemePrefix(beforeFragment)
            val rest = if (scheme != null) beforeFragment.substring(scheme.length + 1) else beforeFragment
            val schemeIsValid = scheme == null || (isScheme(scheme) && rest.isNotEmpty())
            val opaqueHasNoBrackets = scheme == null || rest.startsWith('/') || ('[' !in rest && ']' !in rest)
            return hasLegalCharacters(value) && singleFragment && schemeIsValid && opaqueHasNoBrackets
        }

        /** The text before the first ':' that comes ahead of any '/' or '?', if there is one. */
        private fun schemePrefix(beforeFragment: String): String? {
            val end = beforeFragment.indexOfFirst { it == ':' || it == '/' || it == '?' }
            return if (end >= 0 && beforeFragment[end] == ':') beforeFragment.substring(0, end) else null
        }

        private fun parseHierarchical(
            scheme: String?,
            pathAndAuthority: String,
        ): UriParts? =
            if (pathAndAuthority.startsWith("//")) {
                parseWithAuthority(scheme, pathAndAuthority.substring(2))
            } else {
                pathAndAuthority
                    .takeIf { '[' !in it && ']' !in it }
                    ?.percentDecoded()
                    ?.let { UriParts(scheme, host = null, path = it) }
            }

        private fun parseWithAuthority(
            scheme: String?,
            afterSlashes: String,
        ): UriParts? {
            val authority = afterSlashes.substringBefore('/')
            val path = afterSlashes.substring(authority.length)
            val hostAndPort = authority.substringAfterLast('@')
            val userInfo = authority.substringBeforeLast('@', missingDelimiterValue = "")
            // `//` must be followed by an authority or a path: "https://" alone is malformed.
            val isValid =
                (authority.isNotEmpty() || path.isNotEmpty()) &&
                    '[' !in path &&
                    ']' !in path &&
                    '[' !in userInfo &&
                    isAcceptableHostAndPort(hostAndPort)
            if (!isValid) return null
            // Userinfo cannot contain '@'; java.net.URI then treats the authority as registry-based.
            val host = serverHost(hostAndPort)?.takeIf { authority.count { it == '@' } <= 1 }
            return path.percentDecoded()?.let { UriParts(scheme, host = host, path = it) }
        }

        /** Whether `java.net.URI` would accept this host-and-port at all, host or not. */
        private fun isAcceptableHostAndPort(hostAndPort: String): Boolean {
            if (!hostAndPort.startsWith('[')) return ']' !in hostAndPort
            val close = hostAndPort.indexOf(']')
            val port = if (close >= 0) hostAndPort.substring(close + 1) else ""
            return close >= 0 &&
                isIpv6Literal(hostAndPort.substring(1, close)) &&
                (port.isEmpty() || (port.startsWith(':') && port.drop(1).all { it.isAsciiDigit() }))
        }

        /** The server-based host, or null where `java.net.URI` falls back to a registry authority. */
        private fun serverHost(hostAndPort: String): String? =
            if (hostAndPort.startsWith('[')) {
                hostAndPort.substring(0, hostAndPort.indexOf(']') + 1)
            } else {
                val name = hostAndPort.substringBefore(':')
                val port = hostAndPort.substringAfter(':', missingDelimiterValue = "")
                name.takeIf { port.all { it.isAsciiDigit() } && (isIpv4(it) || isHostname(it)) }
            }

        private fun hasLegalCharacters(value: String): Boolean {
            var legal = true
            var i = 0
            while (legal && i < value.length) {
                val c = value[i]
                legal =
                    when {
                        c.code < SPACE || c.code == DELETE -> false
                        c.code < ASCII_LIMIT && c in ILLEGAL_ASCII -> false
                        c == '%' ->
                            value.substring(i + 1).take(2).let { it.length == 2 && it.all(::isHexDigit) }.also {
                                i +=
                                    2
                            }
                        else -> !(c.isWhitespace() || c.isISOControl())
                    }
                i++
            }
            return legal
        }

        private fun isScheme(value: String): Boolean =
            value.isNotEmpty() &&
                value[0].isAsciiLetter() &&
                value.all { it.isAsciiLetter() || it.isAsciiDigit() || it in "+-." }

        private fun isIpv4(value: String): Boolean {
            val parts = value.split('.')
            return parts.size == IPV4_PARTS &&
                parts.all(::isOctet)
        }

        private fun isOctet(part: String): Boolean =
            part.length in 1..MAX_OCTET_DIGITS && part.all { it.isAsciiDigit() } && part.toInt() <= MAX_OCTET

        /** RFC 2396 hostname, as `java.net.URI` checks it: the top label must start with a letter. */
        private fun isHostname(value: String): Boolean {
            val labels = value.removeSuffix(".").split('.')
            if (value.isEmpty() || labels.any { it.isEmpty() }) return false
            val validLabels =
                labels.all { label ->
                    label.all { it.isAsciiLetter() || it.isAsciiDigit() || it == '-' } &&
                        !label.startsWith('-') &&
                        !label.endsWith('-')
                }
            return validLabels && labels.last()[0].isAsciiLetter()
        }

        private fun isIpv6Literal(value: String): Boolean =
            value.isNotEmpty() &&
                value.count { it == ':' } >= 2 &&
                value.all { isHexDigit(it) || it == ':' || it == '.' }

        /** Percent-decodes UTF-8 the way `URI.getPath()` does, or null for malformed UTF-8. */
        private fun String.percentDecoded(): String? {
            if ('%' !in this) return this
            val bytes = ArrayList<Byte>(length)
            val out = StringBuilder(length)
            var i = 0
            while (i < length) {
                if (this[i] == '%') {
                    bytes.add(substring(i + 1, i + ESCAPE_LENGTH).toInt(HEX_RADIX).toByte())
                    i += ESCAPE_LENGTH
                } else {
                    if (bytes.isNotEmpty()) {
                        out.append(bytes.toByteArray().decodeToString())
                        bytes.clear()
                    }
                    out.append(this[i])
                    i++
                }
            }
            if (bytes.isNotEmpty()) out.append(bytes.toByteArray().decodeToString())
            return out.toString()
        }

        private fun isHexDigit(c: Char): Boolean = c.isAsciiDigit() || c.lowercaseChar() in 'a'..'f'

        private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

        private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

        private const val SPACE = 0x20
        private const val DELETE = 0x7f
        private const val ASCII_LIMIT = 0x80
        private const val IPV4_PARTS = 4
        private const val MAX_OCTET = 255
        private const val HEX_RADIX = 16
        private const val MAX_OCTET_DIGITS = 3

        /** A percent-escape: '%' and two hex digits. */
        private const val ESCAPE_LENGTH = 3
        private const val ILLEGAL_ASCII = " \"<>\\^`{|}"
    }
}
