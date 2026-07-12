package com.orbin.core.ui.date

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/**
 * Formats an epoch-millis timestamp as a local date and time (e.g. "12 Jul 2026, 14:30"), or null
 * when the timestamp is unknown (<= 0). Used for the time a post was made.
 */
fun formatPostDateTime(epochMillis: Long): String? = epochMillis.formatWith(dateTimeFormatter)

/**
 * Formats an epoch-millis timestamp as a local date (e.g. "12 Jul 2026"), or null when the
 * timestamp is unknown (<= 0). Used for the date a thread was created.
 */
fun formatThreadDate(epochMillis: Long): String? = epochMillis.formatWith(dateFormatter)

private fun Long.formatWith(formatter: DateTimeFormatter): String? =
    if (this <= 0L) {
        null
    } else {
        formatter.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
    }
