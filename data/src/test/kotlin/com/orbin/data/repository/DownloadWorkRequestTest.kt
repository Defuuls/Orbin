package com.orbin.data.repository

import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import androidx.work.WorkRequest
import com.google.common.truth.Truth.assertThat
import com.orbin.data.worker.DownloadWorker
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Newest SDK Robolectric 4.16 ships an image for; the app's target SDK is ahead of it. */
private const val ROBOLECTRIC_SDK = 35

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class DownloadWorkRequestTest {
    @Test
    fun theRequestRequiresAConnectionAndUsesExponentialBackoff() {
        val request = downloadWorkRequest(42L)

        assertThat(request.workSpec.input.getLong(DownloadWorker.KEY_ID, -1L)).isEqualTo(42L)
        assertThat(request.workSpec.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        assertThat(request.workSpec.backoffPolicy).isEqualTo(BackoffPolicy.EXPONENTIAL)
        assertThat(request.workSpec.backoffDelayDuration).isEqualTo(WorkRequest.MIN_BACKOFF_MILLIS)
    }
}
