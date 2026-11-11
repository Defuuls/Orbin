package com.orbin.ios

import com.orbin.domain.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FirstRunTest {
    @Test
    fun aNewReaderCannotStartWithoutAFollowedBoard() =
        runTest {
            val settings = FakeSettings()
            val firstRun = FirstRun(settings, MutableStateFlow(false), backgroundScope)

            val state = firstRun.state.first { it.initialized }
            assertTrue(state.needed)
            assertFalse(state.canStart)

            firstRun.complete().join()
            runCurrent()

            assertTrue(firstRun.state.value.needed)
            assertFalse(settings.settings.value.onboardingCompleted)
        }

    @Test
    fun followingTheFirstBoardDoesNotDismissSetupUntilStartIsPressed() =
        runTest {
            val settings = FakeSettings()
            val follows = MutableStateFlow(false)
            val firstRun = FirstRun(settings, follows, backgroundScope)
            firstRun.state.first { it.initialized }

            firstRun.updateFollow { follows.value = true }.join()
            runCurrent()

            assertTrue(firstRun.state.value.needed)
            assertTrue(firstRun.state.value.canStart)
            assertFalse(settings.settings.value.onboardingCompleted)

            firstRun.complete().join()

            assertFalse(firstRun.state.value.needed)
            assertTrue(settings.settings.value.onboardingCompleted)
        }

    @Test
    fun aReaderWhoAlreadyFollowsBoardsIsNotShownItAndIsMarkedDone() =
        runTest {
            val settings = FakeSettings()
            val firstRun = FirstRun(settings, MutableStateFlow(true), backgroundScope)
            runCurrent()

            assertTrue(firstRun.state.value.initialized)
            assertFalse(firstRun.state.value.needed)
            assertTrue(settings.settings.first { it.onboardingCompleted }.onboardingCompleted)
        }

    @Test
    fun anOlderInstallWhichSkippedAllBoardsIsRequiredToInitializeItsFeed() =
        runTest {
            val settings = FakeSettings().apply { settings.value = settings.value.copy(onboardingCompleted = true) }
            val firstRun = FirstRun(settings, MutableStateFlow(false), backgroundScope)
            val state = firstRun.state.first { it.initialized }

            assertTrue(state.needed)
            assertFalse(state.canStart)
            assertFalse(settings.settings.value.onboardingCompleted)
        }

    @Test
    fun deliberatelyUnfollowingTheLastBoardDoesNotReopenSetupMidSession() =
        runTest {
            val settings = FakeSettings()
            val follows = MutableStateFlow(true)
            val firstRun = FirstRun(settings, follows, backgroundScope)
            firstRun.state.first { it.initialized }

            follows.value = false
            runCurrent()

            assertFalse(firstRun.state.value.needed)
            assertTrue(settings.settings.value.onboardingCompleted)
        }

    @Test
    fun theStartupGateWaitsForBothSettingsAndFollowedBoardsToLoad() =
        runTest {
            val stored = FakeSettings()
            val settingsRead = CompletableDeferred<Unit>()
            val followsRead = CompletableDeferred<Unit>()
            val settings =
                object : SettingsRepository by stored {
                    override val settings =
                        flow {
                            settingsRead.await()
                            emit(stored.settings.value)
                        }
                }
            val firstRun =
                FirstRun(
                    settings,
                    flow {
                        followsRead.await()
                        emit(true)
                    },
                    backgroundScope,
                )
            runCurrent()
            assertFalse(firstRun.state.value.initialized)

            settingsRead.complete(Unit)
            runCurrent()
            assertFalse(firstRun.state.value.initialized)

            followsRead.complete(Unit)
            runCurrent()
            assertTrue(firstRun.state.value.initialized)
            assertFalse(firstRun.state.value.needed)
        }

    @Test
    fun startWaitsForTheFollowWriteToFinishEvenIfTheObserverEmitsEarly() =
        runTest {
            val settings = FakeSettings()
            val follows = MutableStateFlow(false)
            val persisted = CompletableDeferred<Unit>()
            val firstRun = FirstRun(settings, follows, backgroundScope)
            firstRun.state.first { it.initialized }

            val write =
                firstRun.updateFollow {
                    follows.value = true
                    persisted.await()
                }
            runCurrent()
            assertTrue(firstRun.state.value.hasFollows)
            assertTrue(firstRun.state.value.isSaving)
            assertFalse(firstRun.state.value.canStart)

            firstRun.complete().join()
            assertFalse(settings.settings.value.onboardingCompleted)

            persisted.complete(Unit)
            write.join()
            runCurrent()
            assertTrue(firstRun.state.value.canStart)

            firstRun.complete().join()
            assertTrue(settings.settings.value.onboardingCompleted)
        }

    @Test
    fun rapidFollowAndUnfollowWritesStayInOrderAndCannotCompleteWithZeroBoards() =
        runTest {
            val settings = FakeSettings()
            val follows = MutableStateFlow(false)
            val persisted = CompletableDeferred<Unit>()
            val writes = mutableListOf<Boolean>()
            val firstRun = FirstRun(settings, follows, backgroundScope)
            firstRun.state.first { it.initialized }

            val follow =
                firstRun.updateFollow {
                    persisted.await()
                    follows.value = true
                    writes += true
                }
            val unfollow =
                firstRun.updateFollow {
                    follows.value = false
                    writes += false
                }
            runCurrent()
            assertTrue(writes.isEmpty())
            assertFalse(firstRun.state.value.canStart)

            persisted.complete(Unit)
            follow.join()
            unfollow.join()
            runCurrent()
            firstRun.complete().join()

            assertEquals(listOf(true, false), writes)
            assertFalse(firstRun.state.value.canStart)
            assertTrue(firstRun.state.value.needed)
            assertFalse(settings.settings.value.onboardingCompleted)
        }

    @Test
    fun aFailedBoardWriteKeepsSetupOpenAndCanBeRetried() =
        runTest {
            val settings = FakeSettings()
            val follows = MutableStateFlow(false)
            val firstRun = FirstRun(settings, follows, backgroundScope)
            firstRun.state.first { it.initialized }

            firstRun.updateFollow { error("Storage is full") }.join()
            firstRun.complete().join()

            assertEquals("Storage is full", firstRun.state.value.errorMessage)
            assertFalse(firstRun.state.value.isSaving)
            assertTrue(firstRun.state.value.needed)
            assertFalse(settings.settings.value.onboardingCompleted)

            firstRun.updateFollow { follows.value = true }.join()
            runCurrent()
            firstRun.complete().join()

            assertEquals(null, firstRun.state.value.errorMessage)
            assertFalse(firstRun.state.value.needed)
            assertTrue(settings.settings.value.onboardingCompleted)
        }

    @Test
    fun aFailedCompletionWriteKeepsSetupOpenAndStartRetriesIt() =
        runTest {
            val stored = FakeSettings()
            var fail = true
            val settings =
                object : SettingsRepository by stored {
                    override suspend fun setOnboardingCompleted(completed: Boolean) {
                        if (completed && fail) error("Could not save setup")
                        stored.setOnboardingCompleted(completed)
                    }
                }
            val follows = MutableStateFlow(false)
            val firstRun = FirstRun(settings, follows, backgroundScope)
            firstRun.state.first { it.initialized }
            firstRun.updateFollow { follows.value = true }.join()
            runCurrent()

            firstRun.complete().join()
            assertTrue(firstRun.state.value.needed)
            assertTrue(firstRun.state.value.canStart)
            assertEquals("Could not save setup", firstRun.state.value.errorMessage)
            assertFalse(stored.settings.value.onboardingCompleted)

            fail = false
            firstRun.complete().join()
            assertFalse(firstRun.state.value.needed)
            assertTrue(stored.settings.value.onboardingCompleted)
        }

    @Test
    fun startRechecksPersistedFollowsInsteadOfTrustingAnEarlierUiValue() =
        runTest {
            val settings = FakeSettings()
            val follows = MutableStateFlow(false)
            val firstRun = FirstRun(settings, follows, backgroundScope)
            firstRun.state.first { it.initialized }
            follows.value = true
            runCurrent()
            assertTrue(firstRun.state.value.canStart)

            follows.value = false
            firstRun.complete().join()

            assertTrue(firstRun.state.value.needed)
            assertFalse(settings.settings.value.onboardingCompleted)
        }

    @Test
    fun boardEditsCannotRaceAnInProgressCompletionWrite() =
        runTest {
            val stored = FakeSettings()
            val persisted = CompletableDeferred<Unit>()
            val settings =
                object : SettingsRepository by stored {
                    override suspend fun setOnboardingCompleted(completed: Boolean) {
                        if (completed) persisted.await()
                        stored.setOnboardingCompleted(completed)
                    }
                }
            val follows = MutableStateFlow(false)
            val firstRun = FirstRun(settings, follows, backgroundScope)
            firstRun.state.first { it.initialized }
            follows.value = true
            runCurrent()

            val completion = firstRun.complete()
            runCurrent()
            firstRun.updateFollow { follows.value = false }.join()
            assertTrue(firstRun.state.value.isSaving)
            assertTrue(follows.value)

            persisted.complete(Unit)
            completion.join()
            assertFalse(firstRun.state.value.needed)
            assertTrue(stored.settings.value.onboardingCompleted)
        }
}
