package com.orbin.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController

internal actual fun shareFile(path: String) = present(listOf(NSURL.fileURLWithPath(path)))

internal actual fun shareContent(
    text: String,
    url: String?,
) = present(listOfNotNull(text, url?.let { NSURL.URLWithString(it) }))

@OptIn(ExperimentalForeignApi::class)
private fun present(items: List<Any>) {
    val presenter = topViewController() ?: return
    val sheet = UIActivityViewController(activityItems = items, applicationActivities = null)
    sheet.popoverPresentationController?.let { popover ->
        popover.sourceView = presenter.view
        popover.sourceRect =
            presenter.view.bounds.useContents { CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0) }
        popover.permittedArrowDirections = 0uL
    }
    presenter.presentViewController(sheet, animated = true, completion = null)
}
