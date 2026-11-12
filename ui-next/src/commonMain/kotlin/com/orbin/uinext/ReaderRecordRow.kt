package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderType

/** Text-only records use the same compact post geometry without fabricating attachment previews. */
@Composable
internal fun ReaderRecordRow(
    title: String,
    source: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    wideWindow: Boolean = false,
    metadata: String = "",
    detail: String = "",
    unread: String = "",
) {
    val inset = readerPostInset(wideWindow)
    val bodyStyle = if (wideWindow) ReaderType.postBodyWide.withReaderFont() else ReaderType.postBody.withReaderFont()
    val detailStyle =
        if (wideWindow) ReaderType.postDetailsWide.withReaderFont() else ReaderType.postDetails.withReaderFont()
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = ReaderSize.touchTarget)
            .background(reader.background)
            .nextClickable(role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) {},
    ) {
        ReaderPostLayout(wideWindow = wideWindow) { textModifier ->
            Column(textModifier.padding(inset), verticalArrangement = Arrangement.spacedBy(inset)) {
                Text(title, style = bodyStyle, color = reader.subject)
                Text(source, style = detailStyle, color = reader.inkMuted)
                if (metadata.isNotBlank()) Text(metadata, style = detailStyle, color = reader.inkMuted)
                if (detail.isNotBlank()) Text(detail, style = bodyStyle, color = reader.ink)
                if (unread.isNotBlank()) Text(unread, style = detailStyle, color = reader.accent)
            }
        }
        ReaderHairline(Modifier.padding(horizontal = inset))
    }
}

/** Clover selects larger post text for a smallest window side of at least 600dp. */
@Composable
internal fun readerRecordUsesWideText(): Boolean {
    val window = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { minOf(window.width, window.height).toDp() >= ReaderSize.wideMin }
}
