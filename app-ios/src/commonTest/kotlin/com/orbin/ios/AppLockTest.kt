package com.orbin.ios

import com.orbin.core.model.AppSettings
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockTest {
    private val settings = FakeSettings()
    private val answers = ArrayDeque<AuthResult>()
    private var asked = 0

    private fun CoroutineScope.lock(enabled: Boolean): AppLock {
        settings.settings.value = AppSettings(biometricLockEnabled = enabled)
        return lock(settings.settings)
    }

    private fun CoroutineScope.lock(values: Flow<AppSettings>): AppLock =
        AppLock(values, settings::setBiometricLockEnabled, {
            asked++
            answers.removeFirstOrNull() ?: AuthResult.Success
        }, this)

    private suspend fun AppLock.settled() = state.first { !it.obscured && !it.unlocking }

    @Test
    fun failedInitialSettingsReadCannotBeOpenedByForeground() =
        runTest {
            val failure = IllegalStateException("Settings unavailable")
            var observedFailure: Throwable? = null
            val scope =
                CoroutineScope(
                    backgroundScope.coroutineContext + SupervisorJob() +
                        CoroutineExceptionHandler { _, error -> observedFailure = error },
                )
            try {
                val lock = scope.lock(flow { throw failure })
                runCurrent()
                assertEquals(failure, observedFailure)
                lock.onForeground()
                lock.onBackground()
                lock.onForeground()
                assertTrue(lock.state.value.obscured)
                assertEquals(0, asked)
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun foregroundBeforeSettingsCannotOpenTheApp() =
        runTest {
            val values = MutableSharedFlow<AppSettings>(replay = 1)
            val lock = backgroundScope.lock(values)
            runCurrent()

            repeat(2) {
                lock.onForeground()
                assertTrue(lock.state.value.obscured)
                lock.onResignActive()
                lock.onBackground()
            }
            lock.onForeground()
            assertTrue(lock.state.value.obscured)
            assertEquals(0, asked)

            answers += AuthResult.Failed("Canceled")
            values.emit(AppSettings(biometricLockEnabled = true))
            runCurrent()
            assertTrue(lock.state.value.locked)
            assertEquals(1, asked)
        }

    @Test
    fun settingsArrivingInTheBackgroundWaitForForegroundAuthentication() =
        runTest {
            val values = MutableSharedFlow<AppSettings>(replay = 1)
            val lock = backgroundScope.lock(values)
            runCurrent()
            lock.onResignActive()
            lock.onBackground()

            values.emit(AppSettings(biometricLockEnabled = true))
            runCurrent()
            assertTrue(lock.state.value.locked)
            assertTrue(lock.state.value.obscured)
            assertEquals(0, asked)

            answers += AuthResult.Failed("Canceled")
            lock.onForeground()
            runCurrent()
            assertTrue(lock.state.value.locked)
            assertFalse(lock.state.value.obscured)
            assertEquals(1, asked)
        }

    @Test
    fun settingsArrivingWhileInactiveKeepTheCoverUntilReturn() =
        runTest {
            val values = MutableSharedFlow<AppSettings>(replay = 1)
            val lock = backgroundScope.lock(values)
            runCurrent()
            lock.onResignActive()

            values.emit(AppSettings(biometricLockEnabled = true))
            runCurrent()
            assertTrue(lock.state.value.obscured)
            assertEquals(0, asked)

            lock.onForeground()
            runCurrent()
            assertFalse(lock.state.value.locked)
            assertFalse(lock.state.value.obscured)
            assertEquals(1, asked)
        }

    @Test
    fun delayedUnlockedSettingsOpenWithoutAForegroundCallback() =
        runTest {
            val values = MutableSharedFlow<AppSettings>(replay = 1)
            val lock = backgroundScope.lock(values)
            runCurrent()
            assertTrue(lock.state.value.obscured)

            values.emit(AppSettings(biometricLockEnabled = false))
            runCurrent()
            assertEquals(LockState(obscured = false), lock.state.value)
            assertEquals(0, asked)
        }

    @Test
    fun delayedUnlockedSettingsInBackgroundOpenOnReturnWithoutAuthentication() =
        runTest {
            val values = MutableSharedFlow<AppSettings>(replay = 1)
            val lock = backgroundScope.lock(values)
            lock.onBackground()

            values.emit(AppSettings(biometricLockEnabled = false))
            runCurrent()
            assertTrue(lock.state.value.obscured)
            lock.onForeground()
            assertEquals(LockState(obscured = false), lock.state.value)
            assertEquals(0, asked)
        }

    @Test
    fun withTheLockOffTheAppOpensAtOnce() =
        runTest {
            val lock = backgroundScope.lock(enabled = false)

            assertEquals(LockState(obscured = false), lock.settled())
            lock.onBackground()
            lock.onForeground()
            assertFalse(lock.state.value.locked)
            assertEquals(0, asked)
        }

    @Test
    fun withTheLockOnTheAppAsksAtLaunchAndOpensOnSuccess() =
        runTest {
            val lock = backgroundScope.lock(enabled = true)

            assertFalse(lock.settled().locked)
            assertEquals(1, asked)
        }

    @Test
    fun aFailedAttemptStaysLockedWithItsReasonUntilTriedAgain() =
        runTest {
            answers += AuthResult.Failed("Face not recognized")
            val lock = backgroundScope.lock(enabled = true)

            val failed = lock.settled()
            assertTrue(failed.locked)
            assertEquals("Face not recognized", failed.message)

            // The Face ID sheet taking the app off screen and back does not ask again by itself.
            lock.onResignActive()
            lock.onForeground()
            assertEquals(1, asked)

            lock.unlock()?.join()
            assertFalse(lock.state.value.locked)
            assertNull(lock.state.value.message)
        }

    @Test
    fun goingToTheBackgroundLocksAndComingBackAsks() =
        runTest {
            val lock = backgroundScope.lock(enabled = true)
            lock.settled()

            lock.onResignActive()
            assertTrue(lock.state.value.obscured, "hidden in the app switcher")
            lock.onBackground()
            assertTrue(lock.state.value.locked)

            lock.onForeground()
            assertFalse(lock.settled().locked)
            assertEquals(2, asked)
        }

    @Test
    fun turningTheLockOnNeedsTheOwnerAndTurningItOffDoesNot() =
        runTest {
            val lock = backgroundScope.lock(enabled = false)
            lock.settled()

            answers += AuthResult.Failed("Canceled")
            lock.setLockEnabled(true).join()
            assertFalse(settings.settings.value.biometricLockEnabled, "not proven, not on")

            lock.setLockEnabled(true).join()
            assertTrue(settings.settings.value.biometricLockEnabled)
            assertFalse(lock.state.value.locked, "it locks next time the app leaves, not now")

            val before = asked
            lock.setLockEnabled(false).join()
            assertFalse(settings.settings.value.biometricLockEnabled)
            assertEquals(before, asked)
        }

    @Test
    fun turningTheLockOffElsewhereLetsTheReaderIn() =
        runTest {
            answers += AuthResult.Failed("Canceled")
            val lock = backgroundScope.lock(enabled = true)
            assertTrue(lock.settled().locked)

            settings.settings.update { it.copy(biometricLockEnabled = false) }

            assertFalse(lock.state.first { !it.locked }.locked)
        }
}
