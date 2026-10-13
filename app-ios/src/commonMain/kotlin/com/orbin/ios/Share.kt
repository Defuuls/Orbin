package com.orbin.ios

/** Offers the file at [path] to the share sheet: Save to Files, AirDrop, another app. */
internal expect fun shareFile(path: String)

/** Shares text content and an optional URL using the host platform's native share interface. */
internal expect fun shareContent(
    text: String,
    url: String? = null,
)
