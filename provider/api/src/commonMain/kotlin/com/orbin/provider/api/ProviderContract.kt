package com.orbin.provider.api

import com.orbin.core.model.Board
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.Thread

/**
 * Cheap, engine-agnostic invariants every provider result must satisfy.
 *
 * Provider implementations stay responsible for protocol quirks; this contract defines what is
 * allowed to cross the SPI boundary. Tests can call these helpers against fixtures or live-shaped
 * responses without knowing anything about Vichan/LynxChan DTOs.
 */
object ProviderContract {
    fun validateBoards(boards: List<Board>): List<String> =
        buildList {
            val ids = mutableSetOf<String>()
            boards.forEachIndexed { index, board ->
                if (board.id.value.isBlank()) add("boards[$index] has a blank id")
                if (!ids.add(board.id.value)) add("duplicate board id '${board.id.value}'")
            }
        }

    fun validateCatalog(threads: List<CatalogThread>): List<String> =
        buildList {
            val keys = mutableSetOf<String>()
            threads.forEachIndexed { index, thread ->
                val key = thread.key.toString()
                if (!keys.add(key)) add("duplicate catalog thread $key")
                if (!thread.originalPost.isOriginalPost) add("catalog[$index] opening post is not marked OP")
                if (thread.originalPost.threadId != thread.key.thread) {
                    add("catalog[$index] opening post/thread key mismatch")
                }
                if (thread.originalPost.board != thread.key.board) {
                    add("catalog[$index] opening post/board key mismatch")
                }
                validateAttachments("catalog[$index]", thread.originalPost.attachments, this)
            }
        }

    fun validateThread(thread: Thread): List<String> =
        buildList {
            if (!thread.originalPost.isOriginalPost) add("thread opening post is not marked OP")
            if (thread.originalPost.threadId != thread.key.thread) add("thread OP/thread key mismatch")
            if (thread.originalPost.board != thread.key.board) add("thread OP/board key mismatch")
            val postIds = mutableSetOf<Long>()
            thread.allPosts.forEachIndexed { index, post ->
                if (post.threadId != thread.key.thread) add("post[$index] belongs to a different thread")
                if (post.board != thread.key.board) add("post[$index] belongs to a different board")
                if (!postIds.add(post.id.value)) add("duplicate post id ${post.id.value}")
                validateAttachments("post[$index]", post.attachments, this)
            }
        }

    fun requireValidBoards(boards: List<Board>) = requireValid("boards", validateBoards(boards))

    fun requireValidCatalog(threads: List<CatalogThread>) = requireValid("catalog", validateCatalog(threads))

    fun requireValidThread(thread: Thread) = requireValid("thread", validateThread(thread))

    private fun validateAttachments(
        owner: String,
        attachments: List<MediaAttachment>,
        errors: MutableList<String>,
    ) {
        attachments.forEachIndexed { index, media ->
            if (!media.sourceUrl.isAbsoluteHttpsUrl()) {
                errors.add("$owner attachment[$index] sourceUrl is not absolute HTTPS")
            }
            if (!media.thumbnailUrl.isAbsoluteHttpsUrl()) {
                errors.add("$owner attachment[$index] thumbnailUrl is not absolute HTTPS")
            }
            if (media.id.isBlank()) errors.add("$owner attachment[$index] has a blank id")
        }
    }

    /**
     * An absolute `https://` URL with a real host. Hand-rolled rather than `java.net.URI` so it
     * runs on iOS too; `ProviderContractTest` pins it to the same answers `URI` gave.
     */
    private fun String.isAbsoluteHttpsUrl(): Boolean {
        if (!startsWith(HTTPS_PREFIX, ignoreCase = true)) return false
        if (any { it.isWhitespace() || it.isISOControl() }) return false
        val authority =
            substring(HTTPS_PREFIX.length)
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('@')
        val host =
            if (authority.startsWith('[')) {
                authority.substringBefore(']', missingDelimiterValue = "")
            } else {
                authority.substringBefore(':')
            }
        return host.isNotEmpty() && host.all { it.isLetterOrDigit() || it in HOST_PUNCTUATION }
    }

    private fun requireValid(
        kind: String,
        errors: List<String>,
    ) {
        if (errors.isNotEmpty()) {
            throw ProviderException.Parse(
                "Provider $kind contract violated: ${errors.joinToString("; ")}",
            )
        }
    }
}

private const val HTTPS_PREFIX = "https://"

// Letters and digits plus what a DNS name, IPv4 or bracketed IPv6 host may contain. An underscore
// is deliberately absent: java.net.URI treats such a host as having none.
private const val HOST_PUNCTUATION = "-.[:"
