package com.orbin.core.common.lock

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide signal for locking immediately, independent of the usual background/foreground
 * biometric-lock cycle — e.g. a "lock now" button reachable from deep in the UI, far from the
 * activity that actually owns the lock screen.
 */
@Singleton
class AppLockController
    @Inject
    constructor() {
        private val lockRequestsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val lockRequests: SharedFlow<Unit> = lockRequestsFlow.asSharedFlow()

        fun requestLock() {
            lockRequestsFlow.tryEmit(Unit)
        }
    }
