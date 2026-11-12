package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.ReaderSize

object ReaderMediaToolbarTags {
    const val BAR = "reader-media-toolbar"
    const val CONTENT = "reader-media-toolbar-content"
}

/**
 * The standalone viewer's flat, full-width toolbar: Clover 3.0.2's
 * controller_navigation_image_viewer.xml uses toolbar_height (56dp) on #87000000.
 * ImageViewerController and ToolbarMenuView bind filename/subtitle, Back, volume, download and overflow.
 */
@Composable
fun ReaderMediaToolbar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier
            .testTag(ReaderMediaToolbarTags.BAR)
            .fillMaxWidth()
            .background(MEDIA_TOOLBAR_BACKGROUND)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
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

private val MEDIA_TOOLBAR_BACKGROUND = Color(0x87000000)

/** Exact 56dp navigation cell from Clover's Toolbar.java. */
@Composable
fun ReaderMediaBack(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(56.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, label, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/** Clover's title container uses 20dp/14dp text, with title at 5dp and subtitle at 29dp. */
@Composable
fun ReaderMediaTitle(
    filename: String,
    position: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.height(56.dp).padding(start = 16.dp)) {
        Text(
            filename,
            color = Color.White,
            fontSize = with(LocalDensity.current) { 20.dp.toSp() },
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
        Text(
            position,
            color = Color.White,
            fontSize = with(LocalDensity.current) { 14.dp.toSp() },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 29.dp),
        )
    }
}

/** Source-bound action targets: 50dp ordinary, 44dp overflow, all 56dp high with 24dp icons. */
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
            .width(if (overflow) 44.dp else 50.dp)
            .height(56.dp)
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
