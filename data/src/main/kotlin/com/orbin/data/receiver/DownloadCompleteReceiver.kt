package com.orbin.data.receiver

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.orbin.domain.repository.DownloadRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Syncs download records in the local database when the platform [DownloadManager]
 * finishes downloading in the background.
 */
@AndroidEntryPoint
class DownloadCompleteReceiver : BroadcastReceiver() {
    @Inject
    lateinit var downloadRepository: DownloadRepository

    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                downloadRepository.refreshStatuses()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
