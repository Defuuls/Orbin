package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_error_network
import com.orbin.uinext.resources.reader_error_network_helper
import com.orbin.uinext.resources.reader_error_not_found
import com.orbin.uinext.resources.reader_error_not_found_helper
import com.orbin.uinext.resources.reader_error_server
import com.orbin.uinext.resources.reader_error_server_helper
import com.orbin.uinext.resources.reader_error_unknown
import com.orbin.uinext.resources.reader_error_unknown_helper
import com.orbin.uinext.resources.reader_loading_catalog
import com.orbin.uinext.resources.reader_loading_thread
import com.orbin.uinext.resources.reader_refresh_failure
import com.orbin.uinext.resources.reader_refreshing
import com.orbin.uinext.resources.reader_reload
import com.orbin.uinext.resources.reader_retry
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.ReaderRadius
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import com.orbin.uinext.tokens.ReaderType
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil

/** A presentation category supplied by the host; shared UI has no provider or domain dependency. */
enum class ReaderFailureCause { NETWORK, NOT_FOUND, SERVER, UNKNOWN }

object ReaderStateTags {
    const val CATALOG_SKELETON = "reader-catalog-skeleton"
    const val THREAD_SKELETON = "reader-thread-skeleton"
    const val SKELETON_CELL = "reader-skeleton-cell"
    const val SKELETON_THUMBNAIL = "reader-skeleton-thumbnail"
    const val SKELETON_PILL = "reader-skeleton-pill"
    const val PROGRESS = "reader-refresh-progress"
    const val EMPTY = "reader-empty-state"
    const val ERROR = "reader-error-state"
    const val TITLE = "reader-state-title"
    const val CONTENT = "reader-state-content"
    const val MESSAGE = "reader-state-message"
    const val ACTION = "reader-state-action"
    const val REFRESH_FAILURE = "reader-refresh-failure"
}

/** Reserves the progress token's height so a reload never moves the loaded content. */
@Composable
fun ReaderRefreshProgress(
    refreshing: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.reader_refreshing)
    val colors = reader
    Box(modifier.fillMaxWidth().height(ReaderSize.progress)) {
        if (refreshing) {
            Box(
                Modifier
                    .testTag(ReaderStateTags.PROGRESS)
                    .fillMaxSize()
                    .background(colors.accent)
                    .semantics {
                        contentDescription = label
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    },
            )
        }
    }
}

/** First-load catalog cells use the very same layout primitives as the loaded rows and cards. */
@Composable
fun ReaderCatalogSkeleton(
    modifier: Modifier = Modifier,
    mode: CatalogMode = CatalogMode.LIST,
    wideWindow: Boolean = false,
    showProgress: Boolean = true,
) {
    ReaderSkeleton(
        label = stringResource(Res.string.reader_loading_catalog),
        tag = ReaderStateTags.CATALOG_SKELETON,
        modifier = modifier,
        mode = mode,
        wideWindow = wideWindow,
        showProgress = showProgress,
    )
}

/** First-load post cells reserve the thumbnail and text space before any attachment is decoded. */
@Composable
fun ReaderThreadSkeleton(
    modifier: Modifier = Modifier,
    wideWindow: Boolean = false,
    showProgress: Boolean = true,
) {
    ReaderSkeleton(
        label = stringResource(Res.string.reader_loading_thread),
        tag = ReaderStateTags.THREAD_SKELETON,
        modifier = modifier,
        wideWindow = wideWindow,
        showProgress = showProgress,
        thread = true,
    )
}

@Composable
private fun ReaderSkeleton(
    label: String,
    tag: String,
    modifier: Modifier,
    wideWindow: Boolean,
    showProgress: Boolean,
    mode: CatalogMode = CatalogMode.LIST,
    thread: Boolean = false,
) {
    val colors = reader
    Column(
        Modifier
            .testTag(tag)
            .then(modifier)
            .fillMaxSize()
            .background(colors.background),
    ) {
        if (showProgress) ReaderRefreshProgress()
        BoxWithConstraints(
            Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = label },
        ) {
            val grid = mode == CatalogMode.GRID
            val gridSpec = catalogGridSpec(maxWidth, maxHeight)
            val columns = if (grid) gridSpec.columns else 1
            val cellMinimum = if (wideWindow) ReaderSize.cellMinWide else ReaderSize.cellMin
            val minimum = if (grid) gridSpec.imageHeight else cellMinimum
            val rowCount = ceil(maxHeight.value / minimum.value).toInt().coerceAtLeast(1)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                userScrollEnabled = false,
                contentPadding = if (grid) PaddingValues(ReaderSpace.gridOuter) else PaddingValues(),
                horizontalArrangement = if (grid) Arrangement.spacedBy(ReaderSpace.gridGap) else Arrangement.Start,
                verticalArrangement = if (grid) Arrangement.spacedBy(ReaderSpace.gridGap) else Arrangement.Top,
            ) {
                items(rowCount * columns) {
                    if (grid) {
                        ReaderCatalogCardLayout(
                            imageHeight = gridSpec.imageHeight,
                            modifier =
                                Modifier
                                    .testTag(ReaderStateTags.SKELETON_CELL)
                                    .shadow(ReaderSize.cardElevation, RoundedCornerShape(ReaderRadius.card))
                                    .clip(RoundedCornerShape(ReaderRadius.card))
                                    .background(colors.background),
                            image = { imageModifier -> SkeletonBlock(imageModifier) },
                        ) { textModifier ->
                            Column(textModifier, verticalArrangement = Arrangement.spacedBy(ReaderSpace.line)) {
                                SkeletonLine(ReaderType.cardTitle.withReaderFont(), lines = 2)
                                SkeletonLine(ReaderType.meta.withReaderFont(), Modifier.width(ReaderSize.pillMinWidth))
                            }
                        }
                    } else {
                        ReaderSkeletonCell(wideWindow, thread)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderSkeletonCell(
    wideWindow: Boolean,
    thread: Boolean,
) {
    val colors = reader
    Column(Modifier.testTag(ReaderStateTags.SKELETON_CELL)) {
        ReaderCellLayout(
            wideWindow = wideWindow,
            verticalPadding = if (thread) ReaderSpace.postPadY else ReaderSpace.rowPadY,
            contentMaxWidth = if (thread) ReaderSize.readingMax else Dp.Unspecified,
            thumbnail = { thumbnailModifier ->
                SkeletonBlock(Modifier.testTag(ReaderStateTags.SKELETON_THUMBNAIL).then(thumbnailModifier))
            },
            trailing =
                if (thread) {
                    null
                } else {
                    {
                        SkeletonBlock(
                            Modifier
                                .testTag(ReaderStateTags.SKELETON_PILL)
                                .size(ReaderSize.pillMinWidth, ReaderSize.pillHeight),
                        )
                    }
                },
        ) { textModifier ->
            if (thread) {
                Column(textModifier) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = ReaderSize.touchTarget,
                            ).padding(bottom = ReaderSpace.gapSmall),
                        verticalArrangement = Arrangement.spacedBy(ReaderSpace.line),
                    ) {
                        SkeletonLine(ReaderType.postAuthor.withReaderFont())
                        SkeletonLine(ReaderType.meta.withReaderFont())
                    }
                    SkeletonLine(ReaderType.body.withReaderFont(), lines = 2)
                }
            } else {
                Column(textModifier, verticalArrangement = Arrangement.spacedBy(ReaderSpace.line)) {
                    SkeletonLine(ReaderType.rowTitle.withReaderFont())
                    SkeletonLine(
                        ReaderType.meta.withReaderFont(),
                        Modifier.width(if (wideWindow) ReaderSize.thumbnailWide else ReaderSize.thumbnail),
                    )
                    SkeletonLine(ReaderType.preview.withReaderFont(), lines = 2)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(ReaderSize.hairline).background(colors.hairline))
    }
}

/** Empty and error states occupy the host's area below its app bar. A missing callback hides its action. */
@Composable
fun ReaderEmptyState(
    title: String,
    message: String,
    onReload: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ReaderCenteredState(
        title = title,
        message = message,
        actionLabel = stringResource(Res.string.reader_reload),
        onAction = onReload,
        error = false,
        modifier = Modifier.testTag(ReaderStateTags.EMPTY).then(modifier),
    )
}

@Composable
fun ReaderErrorState(
    cause: ReaderFailureCause,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    ReaderCenteredState(
        title = readerFailureTitle(cause),
        message = message ?: readerFailureMessage(cause),
        actionLabel = stringResource(Res.string.reader_retry),
        onAction = onRetry,
        error = true,
        modifier = Modifier.testTag(ReaderStateTags.ERROR).then(modifier),
    )
}

@Composable
private fun ReaderCenteredState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: (() -> Unit)?,
    error: Boolean,
    modifier: Modifier,
) {
    val colors = reader
    Box(
        modifier.fillMaxSize().background(colors.background).padding(ReaderSpace.gutter),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .testTag(ReaderStateTags.CONTENT)
                .widthIn(max = ReaderSize.readingMax)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ReaderSpace.gapSmall),
        ) {
            Text(
                title,
                color = if (error) colors.error else colors.ink,
                style = ReaderType.stateTitle.withReaderFont(),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(ReaderStateTags.TITLE).semantics { heading() },
            )
            Text(
                message,
                color = colors.inkMuted,
                style = ReaderType.stateBody.withReaderFont(),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(ReaderStateTags.MESSAGE),
            )
            if (onAction != null) ReaderStateAction(actionLabel, onAction)
        }
    }
}

/** Place at the bottom of the host's retained content, as a Coral [OrbinCard] toast. */
@Composable
fun ReaderRefreshFailure(
    cause: ReaderFailureCause,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    OrbinCard(
        modifier =
            Modifier
                .testTag(ReaderStateTags.REFRESH_FAILURE)
                .then(modifier)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                    ),
                ).padding(ReaderSpace.gapSmall)
                .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(OrbinRadius.card),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                message ?: stringResource(Res.string.reader_refresh_failure, readerFailureTitle(cause)),
                style = ReaderType.stateBody.withReaderFont(),
                color = reader.ink,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onRetry != null) {
                OrbinTextButton(
                    label = stringResource(Res.string.reader_retry),
                    onClick = onRetry,
                    modifier = Modifier.testTag(ReaderStateTags.ACTION),
                )
            }
        }
    }
}

@Composable
private fun ReaderStateAction(
    label: String,
    onClick: () -> Unit,
) {
    OrbinTextButton(
        label = label,
        onClick = onClick,
        modifier =
            Modifier
                .testTag(ReaderStateTags.ACTION)
                .widthIn(min = MIN_TOUCH_TARGET),
    )
}

@Composable
fun readerFailureTitle(cause: ReaderFailureCause): String =
    stringResource(
        when (cause) {
            ReaderFailureCause.NETWORK -> Res.string.reader_error_network
            ReaderFailureCause.NOT_FOUND -> Res.string.reader_error_not_found
            ReaderFailureCause.SERVER -> Res.string.reader_error_server
            ReaderFailureCause.UNKNOWN -> Res.string.reader_error_unknown
        },
    )

@Composable
fun readerFailureMessage(cause: ReaderFailureCause): String =
    stringResource(
        when (cause) {
            ReaderFailureCause.NETWORK -> Res.string.reader_error_network_helper
            ReaderFailureCause.NOT_FOUND -> Res.string.reader_error_not_found_helper
            ReaderFailureCause.SERVER -> Res.string.reader_error_server_helper
            ReaderFailureCause.UNKNOWN -> Res.string.reader_error_unknown_helper
        },
    )

@Composable
private fun SkeletonLine(
    style: TextStyle,
    modifier: Modifier = Modifier,
    lines: Int = 1,
) {
    val density = LocalDensity.current
    val lineHeight = with(density) { style.lineHeight.toDp() }
    val barHeight = with(density) { style.fontSize.toDp() }
    Column(modifier) {
        repeat(lines) {
            Box(Modifier.fillMaxWidth().height(lineHeight), contentAlignment = Alignment.CenterStart) {
                SkeletonBlock(Modifier.fillMaxWidth().height(barHeight))
            }
        }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(ReaderRadius.small)).background(reader.surfaceVariant))
}
