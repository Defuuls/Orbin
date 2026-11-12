package com.orbin.ios

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.orbin.core.model.MediaAttachment
import kotlinx.coroutines.flow.distinctUntilChanged

/** The selected attachment is an identity; its current index only describes the current snapshot. */
internal class MediaViewerSelection(
    val pager: PagerState,
    val keys: List<String>,
    val index: Int,
) {
    val key: String get() = keys[index]
}

/**
 * URLs qualify reused attachment ids across sources. Occurrences keep repeated identical
 * attachments legal pager keys; metadata changes such as a filename do not replace a page.
 */
internal fun mediaViewerKeys(files: List<MediaAttachment>): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return files.map { file ->
        val identity = "${file.id.length}:${file.id}${file.sourceUrl.length}:${file.sourceUrl}"
        val occurrence = occurrences[identity] ?: 0
        occurrences[identity] = occurrence + 1
        "$identity:$occurrence"
    }
}

/** Retain the selected file over live updates; removal falls back to its previous clamped index. */
@Composable
internal fun rememberMediaViewerSelection(
    files: List<MediaAttachment>,
    startIndex: Int,
    onPageChanged: (Int) -> Unit,
): MediaViewerSelection {
    val keys = remember(files) { mediaViewerKeys(files) }
    val requestedIndex = startIndex.coerceIn(keys.indices)
    var selectedKey by rememberSaveable { mutableStateOf(keys[requestedIndex]) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(requestedIndex) }
    val exactIndex = keys.indexOf(selectedKey)
    val retainedIndex =
        if (exactIndex >= 0) {
            exactIndex
        } else {
            val identity = selectedKey.substringBeforeLast(':')
            keys.indexOfFirst { it.substringBeforeLast(':') == identity }
        }
    val index = retainedIndex.takeIf { it >= 0 } ?: selectedIndex.coerceIn(keys.indices)
    val pager = rememberPagerState(initialPage = requestedIndex) { keys.size }
    val reportPage by rememberUpdatedState(onPageChanged)
    LaunchedEffect(pager, keys) {
        val targetKey = keys[index]
        val laidOutKey =
            pager.layoutInfo.visiblePagesInfo
                .firstOrNull { it.index == pager.settledPage }
                ?.key
        if (pager.settledPage != index || laidOutKey != targetKey) pager.scrollToPage(index)
        selectedKey = targetKey
        selectedIndex = index
        snapshotFlow {
            pager.layoutInfo.visiblePagesInfo
                .firstOrNull { it.index == pager.settledPage }
                ?.key as? String
        }.distinctUntilChanged().collect { settledKey ->
            val settledIndex = settledKey?.let(keys::indexOf) ?: -1
            if (settledIndex >= 0) {
                selectedKey = keys[settledIndex]
                selectedIndex = settledIndex
                reportPage(settledIndex)
            }
        }
    }
    return MediaViewerSelection(pager, keys, index)
}
