package com.orbin.feature.gallery

import com.orbin.core.model.AppSettings
import com.orbin.core.model.ThreadMemory
import com.orbin.core.model.UserContentFilters
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.domain.thread.projectReplyRows
import kotlinx.collections.immutable.toImmutableList

/** User hiding changes visible pages, never their canonical thread indices or attachment identities. */
internal fun GalleryContentState.Ready.withPostVisibility(
    settings: AppSettings,
    memory: ThreadMemory,
    explicitlyRequestedIndex: Int?,
): GalleryContentState.Ready {
    val thread = sourceThread ?: return this
    val qualified = memory.takeIf { it.key == thread.key } ?: ThreadMemory(thread.key)
    val posts =
        if (thread.isPermanentlyFiltered()) {
            emptyList()
        } else {
            thread.allPosts.filterNot {
                it.isPermanentlyFiltered()
            }
        }
    val owners = posts.flatMap { post -> post.attachments.filterNot { it.isPermanentlyFiltered() }.map { post.id } }
    // Opening a deliberately revealed post reveals that owner only, never permanent exclusions.
    val requestedOwner = explicitlyRequestedIndex?.let(owners::getOrNull)
    val filters = UserContentFilters(settings.postFilterRules)
    val reasons =
        posts
            .mapNotNull { post ->
                val reason =
                    when {
                        post.id == requestedOwner -> null
                        post.id.value in qualified.hiddenPostIds -> "Hidden by you"
                        post.poster.posterId?.let { it in qualified.hiddenPosterIds } == true -> "Hidden poster"
                        else -> filters.reasonFor(post)
                    }
                reason?.let { post.id to it }
            }.toMap()
    val prefix = "${thread.key.provider.value}/${thread.key.board.value}/${thread.key.thread.value}"
    val rows =
        projectReplyRows(
            posts,
            prefix,
            settings.threadLayout == com.orbin.core.model.ThreadLayout.NESTED,
            qualified.foldedPaths,
            reasons,
        )
    val visible =
        rows
            .filter { it.hiddenReason == null && !it.preview && it.path !in qualified.foldedPaths }
            .mapTo(mutableSetOf()) { it.postId }
    val allowed = owners.indices.filterTo(mutableSetOf()) { owners[it] in visible || owners[it] == requestedOwner }
    val pages = media.indices.filter { threadMediaIndices[it] < 0 || threadMediaIndices[it] in allowed }
    return copy(
        media = pages.map { media[it] }.toImmutableList(),
        threadMediaIndices = pages.map { threadMediaIndices[it] }.toImmutableList(),
        pageKeys = pages.map { pageKeys[it] }.toImmutableList(),
        downloadableThreadMediaIndices = allowed,
    )
}
