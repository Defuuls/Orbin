package com.orbin.ios

/** Shares text content and an optional URL using the host platform's native share interface. */
internal expect fun shareContent(
    text: String,
    url: String? = null,
)
