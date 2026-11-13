package com.orbin.data.worker

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/** Caps how many media downloads may talk to the same host at once. */
@Singleton
class DownloadHostGate
    @Inject
    constructor() {
        private val lock = Mutex()
        private val permits = mutableMapOf<String, Semaphore>()

        suspend fun <T> withPermit(
            host: String,
            block: suspend () -> T,
        ): T = semaphoreFor(host).withPermit { block() }

        private suspend fun semaphoreFor(host: String): Semaphore =
            lock.withLock {
                permits.getOrPut(host) { Semaphore(DownloadAttempt.MAX_CONCURRENT_PER_HOST) }
            }
    }
