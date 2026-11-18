package com.orbin.data.web

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class WebViewSearchDataRepositoryTest {
    @Test
    fun `clearing runs on the dispatcher WebView requires`() =
        runTest {
            val main = RecordingDispatcher()
            var clearedOn: Thread? = null
            var clears = 0
            val repository =
                WebViewSearchDataRepository(main) {
                    clears++
                    clearedOn = Thread.currentThread()
                }

            repository.clear()

            assertThat(clears).isEqualTo(1)
            assertThat(main.dispatches).isEqualTo(1)
            assertThat(clearedOn).isSameInstanceAs(main.ranOn)
        }

    @Test
    fun `a failed clear is reported to the caller`() =
        runTest {
            val repository = WebViewSearchDataRepository(RecordingDispatcher()) { error("web view unavailable") }

            val result = runCatching { repository.clear() }

            assertThat(result.exceptionOrNull()).hasMessageThat().isEqualTo("web view unavailable")
        }

    private class RecordingDispatcher : CoroutineDispatcher() {
        var dispatches = 0
        var ranOn: Thread? = null

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            dispatches++
            ranOn = Thread.currentThread()
            block.run()
        }
    }
}
