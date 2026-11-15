package com.orbin.domain.repository

import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadMemoryRepositoryTest {
    @Test
    fun concurrentReadAndFoldUpdatesSurviveWithoutCrossProviderCollisions() =
        runTest {
            val repository = InMemoryThreadMemoryRepository()
            val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(42))
            val other = key.copy(provider = ProviderId("another-site"))
            coroutineScope {
                launch {
                    repeat(
                        100,
                    ) { id -> repository.update(key) { it.copy(seenPostIds = it.seenPostIds + id.toLong()) } }
                }
                launch {
                    repeat(
                        100,
                    ) { id -> repository.update(key) { it.copy(foldedPaths = it.foldedPaths + "root/$id") } }
                }
            }
            assertEquals(100, repository.get(key).seenPostIds.size)
            assertEquals(100, repository.get(key).foldedPaths.size)
            assertTrue(repository.get(other).seenPostIds.isEmpty())
            repository.forget(key)
            assertTrue(repository.get(key).foldedPaths.isEmpty())
        }
}
