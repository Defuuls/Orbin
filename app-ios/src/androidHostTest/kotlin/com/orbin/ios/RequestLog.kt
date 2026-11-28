package com.orbin.ios

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The requests a scripted engine answered. The engine answers on several threads at once (both
 * sites load together), so writes and reads take the same lock, and reads return a snapshot:
 * iterating the live list while a late request lands is a ConcurrentModificationException.
 */
class RequestLog {
    private val lock = Mutex()
    private val urls = mutableListOf<String>()
    private val agents = mutableSetOf<String?>()

    suspend fun record(
        url: String,
        userAgent: String?,
    ) = lock.withLock {
        urls += url
        agents += userAgent
    }

    suspend fun urls(): List<String> = lock.withLock { urls.toList() }

    suspend fun userAgents(): Set<String?> = lock.withLock { agents.toSet() }
}
