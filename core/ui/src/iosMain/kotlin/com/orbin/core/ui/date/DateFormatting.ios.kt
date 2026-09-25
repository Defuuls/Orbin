package com.orbin.core.ui.date

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone

private const val MILLIS_PER_SECOND = 1000.0

internal actual fun formatLocalDate(
    epochMillis: Long,
    pattern: String,
): String =
    NSDateFormatter()
        .apply {
            dateFormat = pattern
            locale = NSLocale.currentLocale
            timeZone = NSTimeZone.localTimeZone
        }.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / MILLIS_PER_SECOND))
