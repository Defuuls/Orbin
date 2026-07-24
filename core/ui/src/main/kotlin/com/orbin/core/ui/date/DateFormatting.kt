package com.orbin.core.ui.date

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 60L * MINUTE_MILLIS
private const val DAY_MILLIS = 24L * HOUR_MILLIS
private const val WEEK_MILLIS = 7L * DAY_MILLIS

/**
 * Formats [epochMillis] as a short relative time from [nowMillis] (e.g. "just now", "5m", "3h",
 * "2d"). Timestamps older than a week — or in the future (clock skew) — fall back to the absolute
 * date. Returns null when the timestamp is unknown (<= 0).
 *
 * [nowMillis] is injectable so the formatting is deterministic and unit-testable.
 */
fun formatRelativeTime(
    epochMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): String? {
    if (epochMillis <= 0L) return null
    val delta = nowMillis - epochMillis
    return when {
        delta < MINUTE_MILLIS && delta >= 0L -> "just now"
        delta < HOUR_MILLIS && delta >= 0L -> "${delta / MINUTE_MILLIS}m"
        delta < DAY_MILLIS && delta >= 0L -> "${delta / HOUR_MILLIS}h"
        delta < WEEK_MILLIS && delta >= 0L -> "${delta / DAY_MILLIS}d"
        else -> formatThreadDate(epochMillis)
    }
}

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
