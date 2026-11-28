package com.orbin.domain.repository

/**
 * What the in-app browser keeps for the sites it opened: their cookies (so logins persist), site
 * storage and cache. Unlike the search's web data, this is kept on purpose, and removed only when
 * the reader clears local activity.
 */
interface InAppBrowserDataRepository {
    suspend fun clear()
}
