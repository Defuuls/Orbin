package com.orbin.data.settings

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.FeedRefreshInterval
import org.junit.Test

/**
 * "Refresh feed on return" changed from a boolean to an interval, so every existing install has
 * the old key and not the new one. Getting this wrong does not fail loudly — it silently hands
 * back a setting the user never chose, which is exactly the sort of change nobody can explain
 * afterwards.
 */
class FeedRefreshIntervalMigrationTest {
    @Test
    fun anExplicitlyDisabledRefreshBecomesNever() {
        assertThat(resolveFeedRefreshInterval(stored = null, legacyRefreshOnReturn = false))
            .isEqualTo(FeedRefreshInterval.NEVER)
    }

    @Test
    fun anEnabledRefreshBecomesAlways() {
        assertThat(resolveFeedRefreshInterval(stored = null, legacyRefreshOnReturn = true))
            .isEqualTo(FeedRefreshInterval.ALWAYS)
    }

    /** A fresh install has neither key and should land on the documented default. */
    @Test
    fun aFreshInstallDefaultsToAlways() {
        assertThat(resolveFeedRefreshInterval(stored = null, legacyRefreshOnReturn = null))
            .isEqualTo(FeedRefreshInterval.ALWAYS)
    }

    @Test
    fun aStoredIntervalWins() {
        assertThat(
            resolveFeedRefreshInterval(
                stored = FeedRefreshInterval.FIFTEEN_MINUTES.name,
                legacyRefreshOnReturn = false,
            ),
        ).isEqualTo(FeedRefreshInterval.FIFTEEN_MINUTES)
    }

    /**
     * A value written by a newer build, or corrupted, must not crash or resurrect the legacy
     * boolean — it falls through to the default like any other unreadable enum in this file.
     */
    @Test
    fun anUnrecognisedStoredValueFallsBackToTheLegacyBoolean() {
        assertThat(resolveFeedRefreshInterval(stored = "EVERY_FORTNIGHT", legacyRefreshOnReturn = false))
            .isEqualTo(FeedRefreshInterval.NEVER)
    }
}
