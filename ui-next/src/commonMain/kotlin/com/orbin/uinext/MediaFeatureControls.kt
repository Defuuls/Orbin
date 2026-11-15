package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.media_features_done
import com.orbin.uinext.resources.media_file_bytes
import com.orbin.uinext.resources.media_file_dimensions
import com.orbin.uinext.resources.media_file_duration
import com.orbin.uinext.resources.media_file_format
import com.orbin.uinext.resources.media_file_information
import com.orbin.uinext.resources.media_file_unknown
import com.orbin.uinext.resources.media_filter_all
import com.orbin.uinext.resources.media_filter_images
import com.orbin.uinext.resources.media_filter_title
import com.orbin.uinext.resources.media_filter_videos
import com.orbin.uinext.resources.media_reverse_search_title
import com.orbin.uinext.tokens.OrbinType
import org.jetbrains.compose.resources.stringResource

@Composable
fun MediaFilterPills(
    filter: MediaKindFilter,
    images: Int,
    videos: Int,
    onChange: (MediaKindFilter) -> Unit,
    modifier: Modifier = Modifier,
    total: Int = images + videos,
    allowEmpty: Boolean = true,
) {
    val labels =
        listOf(
            MediaKindFilter.ALL to stringResource(Res.string.media_filter_all, total),
            MediaKindFilter.IMAGES to stringResource(Res.string.media_filter_images, images),
            MediaKindFilter.VIDEOS to stringResource(Res.string.media_filter_videos, videos),
        )
    Row(
        modifier.horizontalScroll(rememberScrollState()).selectableGroup().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEach { (kind, label) ->
            val selected = filter == kind
            val count =
                when (kind) {
                    MediaKindFilter.ALL -> total
                    MediaKindFilter.IMAGES -> images
                    MediaKindFilter.VIDEOS -> videos
                }
            Text(
                label,
                style = OrbinType.label.withReaderFont(),
                color = if (selected) reader.accent else reader.inkMuted,
                modifier =
                    Modifier
                        .testTag("media-filter:${kind.name}")
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (selected) reader.accent.copy(alpha = 0.14f) else reader.surfaceVariant)
                        .selectable(
                            selected,
                            enabled = allowEmpty || count > 0,
                            role = Role.Tab,
                            onClick = { onChange(kind) },
                        ).padding(horizontal = 14.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
fun MediaFilterDialog(
    filter: MediaKindFilter,
    images: Int,
    videos: Int,
    total: Int,
    onChange: (MediaKindFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = reader.surfaceVariant,
        shape = RoundedCornerShape(22.dp),
        title = { Text(stringResource(Res.string.media_filter_title), color = reader.ink) },
        text = { MediaFilterPills(filter, images, videos, onChange, total = total, allowEmpty = false) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.media_features_done)) } },
    )
}

@Composable
fun ReverseImageSearchDialog(
    imageUrl: String,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = reader.surfaceVariant,
        shape = RoundedCornerShape(22.dp),
        title = { Text(stringResource(Res.string.media_reverse_search_title), color = reader.ink) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                ReverseImageSearchEngine.entries.forEach { engine ->
                    val url = engine.searchUrl(imageUrl)
                    TextButton(onClick = { if (url != null) onOpenUrl(url) }, enabled = url != null) {
                        Text(
                            when (engine) {
                                ReverseImageSearchEngine.GOOGLE_LENS -> "Google Lens"
                                ReverseImageSearchEngine.BING -> "Bing"
                                ReverseImageSearchEngine.YANDEX -> "Yandex"
                                ReverseImageSearchEngine.SAUCENAO -> "SauceNAO"
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.media_features_done)) } },
    )
}

@Composable
fun MediaFileInfoDialog(
    info: MediaFileInfo,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = reader.surfaceVariant,
        shape = RoundedCornerShape(22.dp),
        title = { Text(stringResource(Res.string.media_file_information), color = reader.ink) },
        text = {
            SelectionContainer {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(info.fileName, style = OrbinType.body.withReaderFont(), color = reader.ink)
                    Text(
                        stringResource(
                            Res.string.media_file_format,
                            info.format.uppercase().ifBlank { stringResource(Res.string.media_file_unknown) },
                        ),
                        color = reader.inkMuted,
                    )
                    if (info.width > 0 && info.height > 0) {
                        Text(
                            stringResource(Res.string.media_file_dimensions, info.width, info.height),
                            color = reader.inkMuted,
                        )
                    }
                    if (info.sizeBytes >
                        0
                    ) {
                        Text(stringResource(Res.string.media_file_bytes, info.sizeBytes), color = reader.inkMuted)
                    }
                    info.durationMs?.takeIf { it > 0 }?.let {
                        Text(stringResource(Res.string.media_file_duration, it / 1000), color = reader.inkMuted)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.media_features_done)) } },
    )
}
