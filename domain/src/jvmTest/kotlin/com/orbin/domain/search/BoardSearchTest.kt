package com.orbin.domain.search

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class BoardSearchTest {
    @Test
    fun `results come back in board order`() =
        runTest {
            val outcome =
                searchEachBoard(listOf("a", "b", "c")) { board ->
                    delay(if (board == "a") 30 else 1)
                    listOf("$board-1", "$board-2")
                }

            assertThat(outcome.results).containsExactly("a-1", "a-2", "b-1", "b-2", "c-1", "c-2").inOrder()
            assertThat(outcome.failedBoards).isEqualTo(0)
            assertThat(outcome.allFailed).isFalse()
        }

    @Test
    fun `one failing board leaves the others`() =
        runTest {
            val outcome = searchEachBoard(listOf("a", "b")) { if (it == "a") error("offline") else listOf("b-1") }

            assertThat(outcome.results).containsExactly("b-1")
            assertThat(outcome.failedBoards).isEqualTo(1)
            assertThat(outcome.allFailed).isFalse()
        }

    @Test
    fun `every board failing is an error with the first reason`() =
        runTest {
            val outcome = searchEachBoard<String, String>(listOf("a", "b")) { board -> error("down $board") }

            assertThat(outcome.allFailed).isTrue()
            assertThat(outcome.firstError?.message).isEqualTo("down a")
        }

    @Test
    fun `no matches is not a failure`() =
        runTest {
            val outcome = searchEachBoard(listOf("a")) { emptyList<String>() }

            assertThat(outcome.allFailed).isFalse()
            assertThat(outcome.results).isEmpty()
        }

    @Test
    fun `never searches more boards at once than allowed`() =
        runTest {
            val running = AtomicInteger()
            var peak = 0
            searchEachBoard((1..8).toList(), maxConcurrent = 3) {
                peak = maxOf(peak, running.incrementAndGet())
                delay(5)
                running.decrementAndGet()
                emptyList<Int>()
            }

            assertThat(peak).isAtMost(3)
        }
}
