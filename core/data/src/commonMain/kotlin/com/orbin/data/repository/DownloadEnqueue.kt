package com.orbin.data.repository

import com.orbin.core.model.DownloadStatus
import com.orbin.data.database.entity.DownloadEntity

/** What [DownloadRepositoryImpl.enqueue] should do with rows already stored for the same URL and path. */
sealed class EnqueueDecision {
    data class ReturnExisting(
        val id: Long,
    ) : EnqueueDecision()

    data class Requeue(
        val id: Long,
    ) : EnqueueDecision()

    data object InsertNew : EnqueueDecision()
}

/**
 * Skip a second copy when one is already queued, running, or completed and still on disk.
 * Re-queue a failed download, or a completed one whose file has been deleted, on the same row.
 */
fun decideEnqueue(
    existing: List<DownloadEntity>,
    completedFilePresent: Boolean,
): EnqueueDecision {
    if (existing.isEmpty()) return EnqueueDecision.InsertNew
    val active =
        existing.firstOrNull {
            it.status == DownloadStatus.QUEUED.name || it.status == DownloadStatus.RUNNING.name
        }
    if (active != null) return EnqueueDecision.ReturnExisting(active.id)
    val completed = existing.firstOrNull { it.status == DownloadStatus.COMPLETED.name }
    if (completed != null && completedFilePresent) return EnqueueDecision.ReturnExisting(completed.id)
    val reusable = completed ?: existing.firstOrNull { it.status == DownloadStatus.FAILED.name } ?: existing.first()
    return EnqueueDecision.Requeue(reusable.id)
}
