package com.orbin.provider.api

import com.orbin.core.model.Board
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.Thread
import java.net.URI

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
                validateAttachments("catalog[$index]", thread.originalPost.attachments, this)
            }
        }

    fun validateThread(thread: Thread): List<String> =
        buildList {
            if (!thread.originalPost.isOriginalPost) add("thread opening post is not marked OP")
            if (thread.originalPost.threadId != thread.key.thread) add("thread OP id/key mismatch")
            val postIds = mutableSetOf<Long>()
            thread.allPosts.forEachIndexed { index, post ->
                if (post.threadId != thread.key.thread) add("post[$index] belongs to a different thread")
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
            if (!media.sourceUrl.isAbsoluteHttpUrl()) errors.add("$owner attachment[$index] sourceUrl is not absolute HTTP(S)")
            if (!media.thumbnailUrl.isAbsoluteHttpUrl()) errors.add("$owner attachment[$index] thumbnailUrl is not absolute HTTP(S)")
            if (media.id.isBlank()) errors.add("$owner attachment[$index] has a blank id")
        }
    }

    private fun String.isAbsoluteHttpUrl(): Boolean =
        runCatching {
            val uri = URI(this)
            uri.isAbsolute && uri.host != null && uri.scheme.lowercase() in setOf("http", "https")
        }.getOrDefault(false)

    private fun requireValid(kind: String, errors: List<String>) {
        require(errors.isEmpty()) { "Provider $kind contract violated: ${errors.joinToString("; ")}" }
    }
}
