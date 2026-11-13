package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderSize

object ReaderMediaToolbarTags {
    const val BAR = "reader-media-toolbar"
    const val CONTENT = "reader-media-toolbar-content"
    const val ACTIONS = "reader-media-toolbar-actions"
}

/**
 * Compact media chrome with safe insets, an explicit Close action and persistent download.
 * Normal text fits 56dp; larger device text grows the title and its toolbar together.
 */
@Composable
fun ReaderMediaToolbar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    ReaderMediaToolbarFrame(modifier) {
        Row(
            Modifier
                .testTag(ReaderMediaToolbarTags.CONTENT)
                .fillMaxWidth()
                .heightIn(min = ReaderSize.appBar),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Narrow windows give the filename its own row without shrinking type or action targets. */
@Composable
fun ReaderMediaToolbar(
    filename: String,
    position: String,
    closeLabel: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    ReaderMediaToolbarFrame(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < MEDIA_TOOLBAR_STACKED_WIDTH) {
                Column(Modifier.fillMaxWidth().testTag(ReaderMediaToolbarTags.CONTENT)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = ReaderSize.appBar,
                            ).testTag(ReaderMediaToolbarTags.ACTIONS),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReaderMediaClose(closeLabel, onClose)
                        Spacer(Modifier.weight(1f))
                        actions()
                    }
                    ReaderMediaTitle(filename, position, Modifier.fillMaxWidth().padding(end = 16.dp))
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = ReaderSize.appBar).testTag(ReaderMediaToolbarTags.CONTENT),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReaderMediaClose(closeLabel, onClose)
                    ReaderMediaTitle(filename, position, Modifier.weight(1f))
                    actions()
                }
            }
        }
    }
}

@Composable
private fun ReaderMediaToolbarFrame(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .testTag(ReaderMediaToolbarTags.BAR)
            .fillMaxWidth()
            .background(MEDIA_TOOLBAR_BACKGROUND)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        content()
    }
}

private val MEDIA_TOOLBAR_BACKGROUND = Color(0x87000000)
private val MEDIA_TOOLBAR_STACKED_WIDTH = 360.dp

/** A persistent, explicit exit for the full-screen viewer. */
@Composable
fun ReaderMediaClose(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(56.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Close, label, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/** A scalable two-line title; large device text grows the toolbar instead of overlapping it. */
@Composable
fun ReaderMediaTitle(
    filename: String,
    position: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.heightIn(min = 56.dp).padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            filename,
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            style = OrbinType.appBarTitle.withReaderFont().copy(fontSize = 20.sp, lineHeight = 24.sp),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            position,
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            style = OrbinType.appBarSubtitle.withReaderFont().copy(fontSize = 14.sp, lineHeight = 20.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Viewer actions keep at least 48dp targets; ordinary actions remain 50dp wide. */
@Composable
fun ReaderMediaAction(
    imageVector: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    overflow: Boolean = false,
) {
    Box(
        modifier
            .width(if (overflow) maxOf(44.dp, ReaderSize.touchTarget) else 50.dp)
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (overflow) Modifier.padding(start = 8.dp, end = 16.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector,
            label,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.requiredSize(24.dp),
        )
    }
}
