package com.orbin.ios

import com.orbin.core.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppLockTest {
    private val settings = FakeSettings()
    private val answers = ArrayDeque<AuthResult>()
    private var asked = 0

    private fun CoroutineScope.lock(enabled: Boolean): AppLock {
        settings.settings.value = AppSettings(biometricLockEnabled = enabled)
        return AppLock(settings.settings, settings::setBiometricLockEnabled, {
            asked++
            answers.removeFirstOrNull() ?: AuthResult.Success
        }, this)
    }

    private suspend fun AppLock.settled() = state.first { !it.obscured && !it.unlocking }

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
