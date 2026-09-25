package com.orbin.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.orbin.core.model.BoardId
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.ProviderId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The shared board preferences over a real DataStore file, on the Android host and on iOS. */
class BoardPreferencesStoreTest {
    private val site = ProviderId("site")
    private val other = ProviderId("other")

    private fun CoroutineScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(scope = this) {
            FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "board-prefs-${Random.nextLong()}.preferences_pb"
        }

    @Test
    fun followingABoardIsKeptPerSite() =
        runTest {
            val store = BoardPreferencesStore(backgroundScope.dataStore())

            store.setSubscribedBoard(site, BoardId("g"), subscribed = true)
            store.setSubscribedBoard(site, BoardId("b"), subscribed = true)
            store.setSubscribedBoard(site, BoardId("b"), subscribed = false)

            assertEquals(setOf(BoardId("g")), store.observeSubscribedBoards(site).first())
            assertEquals(emptySet(), store.observeSubscribedBoards(other).first(), "another site's /g/")
            assertEquals(emptySet(), store.observeFavoriteBoards(site).first(), "following is not favouriting")
        }

    @Test
    fun feedLimitsAreSetClearedAndIgnoredWhenUnreadable() =
        runTest {
            val dataStore = backgroundScope.dataStore()
            val store = BoardPreferencesStore(dataStore)

            store.setFeedThreadLimit(site, BoardId("g"), FeedThreadLimit.SIX)
            assertEquals(FeedThreadLimit.SIX, store.observeFeedThreadLimit(site, BoardId("g")).first())
            assertEquals(
                mapOf(BoardId("g") to FeedThreadLimit.SIX),
                store.observeFeedThreadLimits(site, setOf(BoardId("g"), BoardId("b"))).first(),
            )

            store.setFeedThreadLimit(site, BoardId("g"), null)
            assertNull(store.observeFeedThreadLimit(site, BoardId("g")).first())

            // A name from a future or past version reads as "no override", as it always has.
            dataStore.edit { it[stringPreferencesKey("feed_thread_limit_site_g")] = "SEVEN" }
            assertNull(store.observeFeedThreadLimit(site, BoardId("g")).first())
        }
}
