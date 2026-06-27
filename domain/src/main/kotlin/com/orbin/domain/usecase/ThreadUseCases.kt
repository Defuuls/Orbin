package com.orbin.domain.usecase

import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.BoardId
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.domain.repository.ThreadRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observes a thread for display. Thin by design: orchestration of cache + network lives in the
 * repository; the use case exists so the UI depends on an intention ("observe this thread"),
 * not on a repository surface.
 */
class ObserveThreadUseCase
    @Inject
    constructor(
        private val threadRepository: ThreadRepository,
    ) {
        operator fun invoke(
            provider: ProviderId,
            board: BoardId,
            thread: ThreadId,
        ): Flow<OrbinResult<Thread>> = threadRepository.observeThread(ThreadKey(provider, board, thread))
    }

/**
 * Builds the reply graph for a thread: for every post, the list of posts that reply to it
 * (backlinks). Engines only provide forward quote links, so we invert them here once.
 *
 * Returns a copy of the thread whose posts carry populated [Post.backlinks]. Pure and trivially
 * unit-testable — no I/O.
 */
class BuildReplyGraphUseCase
    @Inject
    constructor() {
        operator fun invoke(thread: Thread): Thread {
            val backlinks: Map<PostId, MutableList<PostId>> =
                buildMap {
                    thread.allPosts.forEach { post ->
                        post.comment.quotedPosts.forEach { quoted ->
                            getOrPut(quoted) { mutableListOf() }.add(post.id)
                        }
                    }
                }

            fun enrich(post: Post): Post {
                val incoming = backlinks[post.id] ?: return post
                return post.copy(backlinks = incoming.toImmutableList())
            }

            return thread.copy(
                originalPost = enrich(thread.originalPost),
                replies = thread.replies.map(::enrich).toImmutableList(),
            )
        }
    }
