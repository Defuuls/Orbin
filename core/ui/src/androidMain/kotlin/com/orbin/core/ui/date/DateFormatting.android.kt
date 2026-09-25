package com.orbin.core.ui.date

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal actual fun formatLocalDate(
    epochMillis: Long,
    pattern: String,
): String =
    DateTimeFormatter
        .ofPattern(pattern, Locale.getDefault())
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
