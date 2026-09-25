package com.orbin.core.model

/** A saved thread as a list shows it, without loading its posts. */
data class SavedThreadSummary(
    val key: ThreadKey,
    val title: String,
    val savedAtMillis: Long,
    val postCount: Int,
)
