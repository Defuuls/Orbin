package com.orbin.ios

import com.orbin.core.model.ProviderId

/** Cached rows stay usable during a retry; progress and errors belong to the requested source. */
internal fun HomeFeedDirectory.boardsOnSite(provider: ProviderId): Load<List<SiteBoard>> {
    val available = boards.filter { it.provider == provider }
    return when {
        available.isNotEmpty() -> Load.Ready(available)
        provider in failures -> Load.Failed(failures.getValue(provider))
        provider in loading -> Load.Loading
        provider in completed -> Load.Ready(emptyList())
        else -> Load.Loading
    }
}

/** The directory already excludes permanently filtered boards before publishing any source. */
internal fun HomeFeedDirectory.eligibleFollowing(
    followed: Set<FollowedBoard>,
    hideNsfw: Boolean,
): Set<FollowedBoard> =
    boards
        .asSequence()
        .filterNot { hideNsfw && it.board.isNsfw }
        .map { FollowedBoard(it.provider, it.board.id) }
        .filter { it in followed }
        .toSet()
