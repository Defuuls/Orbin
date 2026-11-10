package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_downloads_tab
import com.orbin.uinext.resources.next_explore_boards
import com.orbin.uinext.resources.next_launchpad_boards
import com.orbin.uinext.resources.next_nothing_here_yet
import com.orbin.uinext.resources.next_search_query_label
import com.orbin.uinext.resources.next_search_title
import com.orbin.uinext.resources.next_settings_title
import com.orbin.uinext.resources.social_feed_choose_source
import com.orbin.uinext.resources.social_feed_open_thread
import com.orbin.uinext.resources.social_feed_save
import com.orbin.uinext.resources.social_feed_saved_while_watching
import com.orbin.uinext.resources.social_feed_share
import com.orbin.uinext.resources.social_feed_unsave
import com.orbin.uinext.resources.social_feed_unwatch
import com.orbin.uinext.resources.social_feed_view_replies
import com.orbin.uinext.resources.social_feed_watch
import com.orbin.uinext.resources.tiktok_feed_following
import com.orbin.uinext.resources.tiktok_feed_following_empty
import com.orbin.uinext.resources.tiktok_feed_following_hint
import com.orbin.uinext.resources.tiktok_feed_for_you
import com.orbin.uinext.resources.tiktok_feed_hidden
import com.orbin.uinext.resources.tiktok_feed_menu
import com.orbin.uinext.resources.tiktok_feed_open_board
import com.orbin.uinext.resources.tiktok_feed_save_label
import com.orbin.uinext.resources.tiktok_feed_saved_label
import com.orbin.uinext.resources.tiktok_feed_share_label
import com.orbin.uinext.resources.tiktok_feed_source
import com.orbin.uinext.resources.tiktok_feed_thread_source
import com.orbin.uinext.resources.tiktok_feed_view_watched
import com.orbin.uinext.resources.tiktok_feed_watch_label
import com.orbin.uinext.resources.tiktok_feed_watching_label
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TikTokFeedPage(
    row: FeedRow,
    active: Boolean,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)?,
    mediaContent: (@Composable (FeedRow, Modifier, Boolean) -> Unit)?,
    activity: String,
    headerStatus: String? = null,
    onOpen: () -> Unit,
    onOpenBoard: (() -> Unit)?,
    onToggleWatch: ((FeedRow) -> Unit)?,
    onToggleSaved: ((FeedRow) -> Unit)?,
    onShare: ((FeedRow) -> Unit)?,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(TikTokDesign.Black)
            .testTag(TikTokFeedTags.page(row.id)),
    ) {
        val compact = maxHeight < 480.dp || LocalDensity.current.fontScale > 1.5f
        val headerGeometry = rememberTikTokFeedGeometry(maxWidth, headerStatus)
        val topSafeArea = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
        val overlayHeight =
            (maxHeight - topSafeArea - headerGeometry.headerHeight - 26.dp).coerceAtLeast(
                MIN_TOUCH_TARGET,
            )
        val visiblePreview = row.hasPreview && !row.muted
        if (visiblePreview) {
            when {
                mediaContent != null -> mediaContent(row, Modifier.fillMaxSize(), active)
                thumbnail != null -> thumbnail(row, Modifier.fillMaxSize())
            }
        } else {
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(
                        start = 24.dp,
                        end = 84.dp,
                        top = topSafeArea + headerGeometry.headerHeight + 16.dp,
                        bottom = if (compact) 120.dp else 180.dp,
                    ).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (row.muted) {
                    Icon(ReaderIcons.Hide, null, tint = TikTokDesign.White, modifier = Modifier.size(32.dp))
                    Text(
                        stringResource(Res.string.tiktok_feed_hidden),
                        color = TikTokDesign.White,
                        style = NextType.body,
                    )
                } else {
                    Text(
                        row.subject,
                        style = NextType.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
                        fontWeight = FontWeight.Bold,
                        color = TikTokDesign.White,
                    )
                    if (row.excerpt.isNotBlank()) {
                        Text(row.excerpt, style = NextType.body, color = TikTokDesign.White)
                    }
                }
            }
        }
        // Readability gradients sit above media but have no pointer input, so video taps and
        // horizontal photo interactions continue to reach the production media layer.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.2f to Color.Transparent,
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.78f),
                ),
            ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 76.dp, bottom = 18.dp)
                .heightIn(min = MIN_TOUCH_TARGET, max = overlayHeight)
                .verticalScroll(rememberScrollState())
                .testTag(TikTokFeedTags.CAPTION)
                .nextClickable(role = Role.Button, onClick = onOpen),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                "@${row.board.trim('/')}",
                style = NextType.bodyMedium.copy(fontSize = 16.sp, lineHeight = 21.sp, shadow = FEED_TEXT_SHADOW),
                fontWeight = FontWeight.Bold,
                color = TikTokDesign.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.hasPreview || row.muted) {
                Text(
                    row.subject,
                    style = NextType.bodyMedium.copy(shadow = FEED_TEXT_SHADOW),
                    color = TikTokDesign.White,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact && !row.muted && row.excerpt.isNotBlank()) {
                    Text(
                        row.excerpt,
                        style = NextType.bodyMedium.copy(shadow = FEED_TEXT_SHADOW),
                        color = TikTokDesign.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text =
                    if (row.threadNumber.isNotBlank()) {
                        stringResource(Res.string.tiktok_feed_thread_source, row.sourceAndBoard, row.threadNumber)
                    } else {
                        stringResource(Res.string.tiktok_feed_source, row.sourceAndBoard, activity)
                    },
                style = NextType.footnote.copy(fontSize = 12.sp, lineHeight = 16.sp, shadow = FEED_TEXT_SHADOW),
                color = TikTokDesign.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TikTokFeedActions(
            row = row,
            onOpen = onOpen,
            onOpenBoard = onOpenBoard,
            onToggleWatch = onToggleWatch,
            onToggleSaved = onToggleSaved,
            onShare = onShare,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 3.dp, bottom = 12.dp)
                    .heightIn(max = overlayHeight),
        )
        val needsThreadPlayback = mediaContent == null && visiblePreview && row.mediaLabel.isVideoLabel()
        if (needsThreadPlayback) {
            NextIconAction(
                ReaderIcons.Play,
                stringResource(Res.string.social_feed_open_thread),
                onOpen,
                tint = TikTokDesign.White,
                modifier = Modifier.align(Alignment.Center).size(64.dp),
            )
        }
    }
}

private fun String.isVideoLabel(): Boolean =
    lowercase().removePrefix(".") in setOf("webm", "mp4", "video", "mov", "m4v")

@Composable
private fun TikTokFeedActions(
    row: FeedRow,
    onOpen: () -> Unit,
    onOpenBoard: (() -> Unit)?,
    onToggleWatch: ((FeedRow) -> Unit)?,
    onToggleSaved: ((FeedRow) -> Unit)?,
    onShare: ((FeedRow) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.width(56.dp).verticalScroll(rememberScrollState()).testTag(TikTokFeedTags.ACTIONS),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val boardDescription = stringResource(Res.string.tiktok_feed_open_board, row.board)
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(1.5.dp, TikTokDesign.White, CircleShape)
                .then(
                    if (onOpenBoard != null) {
                        Modifier.nextClickable(role = Role.Button, onClick = onOpenBoard).semantics {
                            contentDescription = boardDescription
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            BoardIcon(row.site, row.board, title = row.boardTitle, modifier = Modifier.fillMaxSize().padding(2.dp))
        }
        if (onToggleWatch != null) {
            TikTokRailAction(
                icon = ReaderIcons.HeartFilled,
                label =
                    stringResource(
                        if (row.watched) Res.string.tiktok_feed_watching_label else Res.string.tiktok_feed_watch_label,
                    ),
                description =
                    stringResource(
                        if (row.watched) Res.string.social_feed_unwatch else Res.string.social_feed_watch,
                        row.subject,
                    ),
                selected = row.watched,
                onClick = { onToggleWatch(row) },
            )
        }
        TikTokRailAction(
            icon = ReaderIcons.ChatFilled,
            label = row.replies.toString(),
            description = stringResource(Res.string.social_feed_view_replies, row.subject),
            onClick = onOpen,
        )
        if (onToggleSaved != null) {
            TikTokRailAction(
                icon = ReaderIcons.SavedFilled,
                label =
                    stringResource(
                        if (row.bookmarked ||
                            row.watched
                        ) {
                            Res.string.tiktok_feed_saved_label
                        } else {
                            Res.string.tiktok_feed_save_label
                        },
                    ),
                description =
                    stringResource(
                        when {
                            row.watched -> Res.string.social_feed_saved_while_watching
                            row.bookmarked -> Res.string.social_feed_unsave
                            else -> Res.string.social_feed_save
                        },
                        row.subject,
                    ),
                selected = row.bookmarked || row.watched,
                enabled = !row.watched,
                onClick = { onToggleSaved(row) },
            )
        }
        if (onShare != null) {
            TikTokRailAction(
                icon = ReaderIcons.ShareForward,
                label = stringResource(Res.string.tiktok_feed_share_label),
                description = stringResource(Res.string.social_feed_share, row.subject),
                onClick = { onShare(row) },
            )
        }
    }
}

@Composable
private fun TikTokRailAction(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    Column(
        modifier =
            Modifier
                .width(56.dp)
                .heightIn(min = MIN_TOUCH_TARGET)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    this.selected = selected
                    if (!enabled) disabled()
                }.then(if (enabled) Modifier.nextClickable(role = Role.Button, onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) TikTokDesign.Pink else TikTokDesign.White,
            modifier = Modifier.size(TikTokDesign.ActionIconSize),
        )
        Text(
            label,
            style = NextType.footnote.copy(fontSize = 12.sp, lineHeight = 15.sp, shadow = FEED_TEXT_SHADOW),
            fontWeight = FontWeight.SemiBold,
            color = TikTokDesign.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
    }
}

private val FEED_TEXT_SHADOW = Shadow(Color.Black.copy(alpha = 0.65f), Offset(0f, 1f), 3f)

@Composable
internal fun TikTokFeedHeader(
    following: Boolean,
    onFollowingChange: (Boolean) -> Unit,
    subtitle: String?,
    statusMessage: String?,
    onSettings: (() -> Unit)?,
    onSearch: (() -> Unit)?,
    onOpenBoards: (() -> Unit)?,
    onOpenDownloads: (() -> Unit)?,
    headerContent: @Composable () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    sourceLabels: List<String>,
    selectedSource: Int,
    onSelectSource: (Int) -> Unit,
) {
    var menuVisible by rememberSaveable { mutableStateOf(false) }
    var sourceVisible by rememberSaveable { mutableStateOf(false) }
    var queryVisible by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val headerGeometry = rememberTikTokFeedGeometry(maxWidth, statusMessage)
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ).testTag(TikTokFeedTags.HEADER)
                .semantics { subtitle?.let { stateDescription = it } },
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = TikTokDesign.FeedHeaderHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NextIconAction(
                    ReaderIcons.Menu,
                    stringResource(Res.string.tiktok_feed_menu),
                    onClick = { menuVisible = true },
                    tint = TikTokDesign.White,
                )
                if (headerGeometry.stackedTabs) {
                    Spacer(Modifier.weight(1f))
                } else {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TikTokFeedTab(
                                label = stringResource(Res.string.tiktok_feed_following),
                                selected = following,
                                tag = TikTokFeedTags.FOLLOWING,
                                onClick = { onFollowingChange(true) },
                            )
                            TikTokFeedTab(
                                label = stringResource(Res.string.tiktok_feed_for_you),
                                selected = !following,
                                tag = TikTokFeedTags.FOR_YOU,
                                onClick = { onFollowingChange(false) },
                            )
                        }
                    }
                }
                NextIconAction(
                    ReaderIcons.Search,
                    stringResource(Res.string.next_search_title),
                    onClick = { if (onSearch != null) onSearch() else queryVisible = true },
                    tint = TikTokDesign.White,
                )
            }
            if (headerGeometry.stackedTabs) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    TikTokFeedTab(
                        label = stringResource(Res.string.tiktok_feed_following),
                        selected = following,
                        tag = TikTokFeedTags.FOLLOWING,
                        onClick = { onFollowingChange(true) },
                        modifier = Modifier.fillMaxWidth(),
                        wrap = true,
                    )
                    TikTokFeedTab(
                        label = stringResource(Res.string.tiktok_feed_for_you),
                        selected = !following,
                        tag = TikTokFeedTags.FOR_YOU,
                        onClick = { onFollowingChange(false) },
                        modifier = Modifier.fillMaxWidth(),
                        wrap = true,
                    )
                }
            }
            if (!statusMessage.isNullOrBlank()) {
                Text(
                    statusMessage,
                    style = NextType.footnote,
                    color = TikTokDesign.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .padding(horizontal = 64.dp)
                            .fillMaxWidth()
                            .background(
                                Color.Black.copy(alpha = 0.72f),
                            ).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
    if (menuVisible || queryVisible) {
        TikTokFeedMenu(
            queryVisible = queryVisible,
            query = query,
            onQueryChange = onQueryChange,
            subtitle = subtitle,
            headerContent = headerContent,
            onChooseSource =
                if (sourceLabels.isNotEmpty()) {
                    {
                        menuVisible = false
                        queryVisible = false
                        sourceVisible = true
                    }
                } else {
                    null
                },
            onSettings = onSettings,
            onOpenBoards = onOpenBoards,
            onOpenDownloads = onOpenDownloads,
            onDismiss = {
                menuVisible = false
                queryVisible = false
            },
        )
    }
    if (sourceVisible) {
        FeedSourceSheet(
            labels = sourceLabels,
            selected = selectedSource,
            onSelect = { index ->
                onSelectSource(index)
                sourceVisible = false
            },
            onDismiss = { sourceVisible = false },
        )
    }
}

@Composable
private fun TikTokFeedTab(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wrap: Boolean = false,
) {
    Column(
        modifier
            .widthIn(min = MIN_TOUCH_TARGET)
            .heightIn(min = MIN_TOUCH_TARGET)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .testTag(tag)
            .semantics {
                contentDescription = label
                heading()
            }.padding(start = 4.dp, end = 4.dp, top = 9.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            label,
            style = TikTokFeedTabStyle.copy(shadow = FEED_TEXT_SHADOW),
            fontWeight = FontWeight.Bold,
            color = if (selected) TikTokDesign.White else TikTokDesign.SecondaryWhite,
            maxLines = if (wrap) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Clip,
            textAlign = if (wrap) TextAlign.Center else TextAlign.Start,
            modifier = (if (wrap) Modifier.fillMaxWidth() else Modifier).semantics { hideFromAccessibility() },
        )
        Box(Modifier.width(22.dp).height(2.dp).background(if (selected) TikTokDesign.White else Color.Transparent))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TikTokFeedMenu(
    queryVisible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    subtitle: String?,
    headerContent: @Composable () -> Unit,
    onChooseSource: (() -> Unit)?,
    onSettings: (() -> Unit)?,
    onOpenBoards: (() -> Unit)?,
    onOpenDownloads: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = next.background,
        contentColor = next.ink,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.tiktok_feed_menu), style = NextType.titleMedium, color = next.ink)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = NextType.footnote, color = next.muted)
            headerContent()
            if (queryVisible || query.isNotBlank()) {
                SchematicSearch(query, onQueryChange, stringResource(Res.string.next_search_query_label))
            }
            val closeAfter: (() -> Unit) -> (() -> Unit) = { action ->
                {
                    onDismiss()
                    action()
                }
            }
            onChooseSource?.let {
                InlineAction(
                    stringResource(Res.string.social_feed_choose_source),
                    onClick = it,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            onOpenBoards?.let {
                InlineAction(
                    stringResource(Res.string.next_launchpad_boards),
                    onClick = closeAfter(it),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            onOpenDownloads?.let {
                InlineAction(
                    stringResource(Res.string.next_downloads_tab),
                    onClick = closeAfter(it),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            onSettings?.let {
                InlineAction(
                    stringResource(Res.string.next_settings_title),
                    onClick = closeAfter(it),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun TikTokFeedEmpty(
    following: Boolean,
    onOpenBoards: (() -> Unit)?,
    onOpenWatched: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 80.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(ReaderIcons.Heart, null, tint = TikTokDesign.White, modifier = Modifier.size(40.dp))
            Text(
                stringResource(
                    if (following) Res.string.tiktok_feed_following_empty else Res.string.next_nothing_here_yet,
                ),
                style = NextType.titleMedium,
                color = TikTokDesign.White,
            )
            if (following) {
                Text(
                    stringResource(Res.string.tiktok_feed_following_hint),
                    style = NextType.body,
                    color = TikTokDesign.SecondaryWhite,
                )
            }
            val action = if (following) onOpenWatched else onOpenBoards
            if (action != null) {
                Text(
                    stringResource(
                        if (following) Res.string.tiktok_feed_view_watched else Res.string.next_explore_boards,
                    ),
                    style = NextType.bodyMedium,
                    color = TikTokDesign.White,
                    modifier =
                        Modifier
                            .heightIn(
                                min = MIN_TOUCH_TARGET,
                            ).nextClickable(role = Role.Button, onClick = action)
                            .padding(vertical = 12.dp),
                )
            }
        }
    }
}
