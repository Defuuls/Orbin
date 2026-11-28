package com.orbin.data.web

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AndroidInAppBrowserDataRepositoryTest {
    @Test
    fun `clearing hands the work to the browser process`() =
        runTest {
            var starts = 0
            AndroidInAppBrowserDataRepository { starts++ }.clear()

            assertThat(starts).isEqualTo(1)
        }

    @Test
    fun `a refused start is reported, not swallowed`() =
        runTest {
            val repository = AndroidInAppBrowserDataRepository { error("Browser data service unavailable") }

            val result = runCatching { repository.clear() }

            assertThat(result.exceptionOrNull()).hasMessageThat().isEqualTo("Browser data service unavailable")
        }
}
