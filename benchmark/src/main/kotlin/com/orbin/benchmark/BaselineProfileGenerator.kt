package com.orbin.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * Records real launcher startup and the same populated reader journeys as the frame benchmarks.
 * Rooted API 35 CI runs on relevant PRs, monthly, and manually; PR runs upload artifacts only.
 * Fixture orchestration classes are filtered from the shipping profile at collection and merge.
 */
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @After
    fun restoreWindow() = resetReaderWindow()

    @Test
    fun startup() {
        rule.collect(
            packageName = READER_PACKAGE,
            includeInStartupProfile = true,
            filterPredicate = ::shippingProfileRule,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    @Test
    fun catalogList() = collectReader(ReaderJourney.LIST)

    @Test
    fun catalogGrid() = collectReader(ReaderJourney.GRID)

    @Test
    fun thread() = collectReader(ReaderJourney.THREAD)

    @Test
    fun files() = collectReader(ReaderJourney.FILES)

    @Test
    fun twoPane() = collectReader(ReaderJourney.TWO_PANE)

    private fun collectReader(journey: ReaderJourney) {
        rule.collect(
            packageName = READER_PACKAGE,
            filterPredicate = ::shippingProfileRule,
        ) {
            startReaderJourney(journey)
            exerciseReaderJourney(journey)
        }
    }
}

private fun shippingProfileRule(rule: String): Boolean = !rule.contains("Lcom/orbin/app/benchmark/")
