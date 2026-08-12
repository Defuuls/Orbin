package com.orbin.core.ui.thread

import com.orbin.core.model.ThreadStats

/** Summary labels for a thread's stats, ready for rendering. Includes posters only if count > 0. */
fun ThreadStats.summaryLabels(): List<String> =
    buildList {
        add("$replyCount replies")
        add("$imageCount media")
        if (uniquePosterCount > 0) {
            add("$uniquePosterCount posters")
        }
        if (isClosed) {
            add("closed")
        }
        if (isArchived) {
            add("archived")
        }
    }
