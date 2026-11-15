package com.orbin.graph

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin

/** One owner for history policy and automatic-copy observers; platform clients/database remain external. */
internal class RepositoryLifetime(
    dispatcher: CoroutineDispatcher,
) {
    private val job = SupervisorJob()
    val scope = CoroutineScope(job + dispatcher)

    suspend fun shutdown() {
        job.cancelAndJoin()
    }
}
