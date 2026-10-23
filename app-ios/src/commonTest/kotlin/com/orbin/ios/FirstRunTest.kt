package com.orbin.ios

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FirstRunTest {
    @Test
    fun aNewReaderSeesThePickerUntilTheyStartBrowsing() =
        runTest {
            val settings = FakeSettings()
            val firstRun = FirstRun(settings, MutableStateFlow(false), backgroundScope)

            assertTrue(firstRun.needed.first { it })

            firstRun.complete().join()
            runCurrent()

            assertFalse(firstRun.needed.value)
            assertTrue(settings.settings.value.onboardingCompleted)
        }

    @Test
    fun aReaderWhoAlreadyFollowsBoardsIsNotShownItAndIsMarkedDone() =
        runTest {
            val settings = FakeSettings()
            val firstRun = FirstRun(settings, MutableStateFlow(true), backgroundScope)
            runCurrent()

            assertFalse(firstRun.needed.value)
            assertTrue(settings.settings.first { it.onboardingCompleted }.onboardingCompleted)
        }
}
