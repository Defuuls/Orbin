package com.orbin.feature.home

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.FeedRefreshInterval
import org.junit.Test

/**
 * "Refresh feed on return" used to be a switch; it is now an interval, and the two ends have to
 * keep meaning what the switch meant. `ALWAYS` carries a staleness bound of zero and `NEVER` has
 * none at all, which is easy to get backwards — an off-by-one in either direction turns "keep the
 * feed as I left it" into "reload every time", or the reverse.
 */
class FeedRefreshIntervalTest {
    @Test
    fun alwaysNeverReusesACachedFeed() {
        assertThat(FeedRefreshInterval.ALWAYS.allowsReuse(ageMillis = 0)).isFalse()
        assertThat(FeedRefreshInterval.ALWAYS.allowsReuse(ageMillis = 1)).isFalse()
    }

    @Test
    fun neverAlwaysReusesACachedFeed() {
        assertThat(FeedRefreshInterval.NEVER.allowsReuse(ageMillis = 0)).isTrue()
        assertThat(FeedRefreshInterval.NEVER.allowsReuse(ageMillis = Long.MAX_VALUE)).isTrue()
    }

    @Test
    fun aFeedYoungerThanTheIntervalIsReused() {
        assertThat(FeedRefreshInterval.FIVE_MINUTES.allowsReuse(ageMillis = FIVE_MINUTES_MS - 1)).isTrue()
    }

    /** At exactly the interval the feed is due, not still fresh — otherwise "5 min" means "5 min and a bit". */
    @Test
    fun aFeedAtOrPastTheIntervalIsReloaded() {
        assertThat(FeedRefreshInterval.FIVE_MINUTES.allowsReuse(ageMillis = FIVE_MINUTES_MS)).isFalse()
        assertThat(FeedRefreshInterval.FIVE_MINUTES.allowsReuse(ageMillis = FIVE_MINUTES_MS + 1)).isFalse()
    }

    @Test
    fun theIntervalsAreOrderedFromMostToLeastFrequent() {
        val bounded =
            FeedRefreshInterval.entries
                .filter { it != FeedRefreshInterval.NEVER }
                .map { checkNotNull(it.staleAfterMillis) }

        assertThat(bounded).isInStrictOrder()
        assertThat(FeedRefreshInterval.entries.last()).isEqualTo(FeedRefreshInterval.NEVER)
    }

    private companion object {
        const val FIVE_MINUTES_MS = 5 * 60_000L
    }
}
