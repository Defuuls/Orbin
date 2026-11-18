package com.orbin.domain.repository

/**
 * What the "All of 4chan" search leaves behind in the platform web view: the search site's and its
 * verification service's cookies, site storage and cached pages.
 *
 * The rest of the app keeps site cookies in memory only. A web view keeps its own, on disk, so
 * they are removed when the search closes and again whenever the reader clears local activity.
 */
interface WebSearchDataRepository {
    suspend fun clear()
}
