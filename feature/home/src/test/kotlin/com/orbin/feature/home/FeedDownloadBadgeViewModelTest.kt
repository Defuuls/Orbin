package com.orbin.feature.home

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.DownloadRecord
import com.orbin.core.model.DownloadStatus
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.domain.repository.DownloadRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedDownloadBadgeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the badge follows active transfers without emitting for byte progress`() =
        runTest {
            val records = MutableStateFlow<List<DownloadRecord>>(emptyList())
            val repository = mockk<DownloadRepository>()
            every { repository.observeDownloads() } returns records
            val model = FeedDownloadBadgeViewModel(repository)
            val counts = mutableListOf<Int>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.activeCount.toList(counts) }
            runCurrent()

            records.value =
                listOf(
                    record(1, DownloadStatus.QUEUED),
                    record(2, DownloadStatus.RUNNING),
                    record(3, DownloadStatus.COMPLETED),
                    record(4, DownloadStatus.FAILED),
                )
            runCurrent()
            assertThat(model.activeCount.value).isEqualTo(2)

            records.value = records.value.map { it.copy(downloadedBytes = 50, totalBytes = 100) }
            runCurrent()
            assertThat(counts).containsExactly(0, 2).inOrder()

            records.value = records.value.map { it.copy(status = DownloadStatus.COMPLETED) }
            runCurrent()
            assertThat(model.activeCount.value).isEqualTo(0)
            assertThat(counts).containsExactly(0, 2, 0).inOrder()
        }

    private fun record(
        id: Long,
        status: DownloadStatus,
    ) = DownloadRecord(id, "https://example.test/$id.webm", "$id.webm", status, createdAtMillis = 1)
}
