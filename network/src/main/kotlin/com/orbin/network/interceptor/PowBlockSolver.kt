package com.orbin.network.interceptor

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs POWBlock mining off OkHttp's shared dispatcher threads.
 *
 * [PowBlockInterceptor] must stay synchronous (OkHttp interceptor contract), but hashing on those
 * threads stalls every other call on the same client — including Coil image loads. Mining happens
 * here on a small dedicated pool; the interceptor only waits with a hard timeout and fails closed.
 */
internal class PowBlockSolver(
    parallelism: Int = DEFAULT_PARALLELISM,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    private val threadIndex = AtomicInteger(0)
    private val executor =
        Executors.newFixedThreadPool(parallelism.coerceAtLeast(1)) { runnable ->
            Thread(runnable, "orbin-pow-${threadIndex.getAndIncrement()}").apply { isDaemon = true }
        }

    /**
     * Mines [challenge] on the dedicated pool. Returns null on timeout, interrupt, or unsolvable
     * challenge so the interceptor can fail closed without hanging the request forever.
     */
    fun solve(challenge: PowBlock.Challenge): Long? {
        val future = executor.submit(Callable { PowBlock.solve(challenge) })
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            null
        } catch (_: ExecutionException) {
            null
        } catch (_: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            null
        }
    }

    private companion object {
        const val DEFAULT_PARALLELISM = 2
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}
