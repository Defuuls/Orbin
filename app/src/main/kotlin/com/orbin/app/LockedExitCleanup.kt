package com.orbin.app

import co.touchlab.kermit.Logger
import com.orbin.core.common.dispatchers.ApplicationScope
import com.orbin.domain.repository.VideoCacheRepository
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What is removed from the device when the reader leaves the app with App lock on.
 *
 * The database, settings and saved files are encrypted; the streaming video cache is not, because
 * the player reads it as ordinary files. With the lock on, recently watched videos must not wait
 * in that cache while the app is closed, so they are deleted on the way out. The video that is
 * open is kept, so it still plays after unlocking.
 *
 * Runs on the application scope: the activity that reports the exit is already stopping.
 */
class LockedExitCleanup
    @Inject
    constructor(
        private val videoCache: Lazy<VideoCacheRepository>,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        fun onLeavingApp(lockEnabled: Boolean) {
            if (!lockEnabled) return
            scope.launch {
                try {
                    videoCache.get().clearExceptOpenVideos()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (
                    @Suppress("TooGenericExceptionCaught") error: Exception,
                ) {
                    // Leaving must never crash the app; the next exit tries again.
                    Logger.w(tag = "LockedExitCleanup") { "Could not clear cached videos (${error::class.simpleName})" }
                }
            }
        }
    }
