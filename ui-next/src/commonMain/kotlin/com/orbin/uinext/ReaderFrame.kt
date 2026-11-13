package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_back
import com.orbin.uinext.resources.reader_menu
import com.orbin.uinext.resources.reader_more
import com.orbin.uinext.tokens.ReaderColors
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import com.orbin.uinext.tokens.ReaderType
import com.orbin.uinext.tokens.readerColors
import org.jetbrains.compose.resources.stringResource

/** The reader design's colors for the brightness mode in scope. */
val reader: ReaderColors
    @Composable get() = readerColors(dark = next.dark, amoled = next.amoled)

/** What the leading control of an app bar does. */
enum class ReaderNavigation {
    /** Opens the navigation drawer. */
    MENU,

    /** Returns to the previous screen, or closes the thread pane. */
    BACK,

    /** No leading control. */
    NONE,
}

/** An app bar action drawn as an icon. [label] is what a screen reader announces. */
@Immutable
class ReaderAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/** An entry in an app bar's overflow menu. A disabled entry is read out but does nothing. */
@Immutable
class ReaderMenuItem(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

object ReaderAppBarTags {
    const val BAR = "reader-app-bar"
    const val TITLE = "reader-app-bar-title"
    const val OVERFLOW = "reader-app-bar-overflow"
    const val BOARD_PICKER = "reader-board-picker-button"
}

/**
 * The app bar of docs/design-system: `size.appBar` tall on `color.appBar`, extended under the
 * status bar. It never scrolls away.
 *
 * [onTitleClick] makes the title a button with a trailing arrow, as the catalog's board picker.
 * [overflow] entries appear behind a More button; with none, the button is not drawn.
 */
@Composable
fun ReaderAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigation: ReaderNavigation = ReaderNavigation.NONE,
    onNavigation: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    titleClickLabel: String? = null,
    onTitleTap: (() -> Unit)? = null,
    titleTapLabel: String? = null,
    actions: List<ReaderAction> = emptyList(),
    overflow: List<ReaderMenuItem> = emptyList(),
) {
    val colors = reader
    val leading = if (onNavigation != null) navigation else ReaderNavigation.NONE
    BoxWithConstraints(
        modifier =
            modifier
                .testTag(ReaderAppBarTags.BAR)
                .fillMaxWidth()
                .background(colors.statusBar)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
    ) {
        val titleStart = if (leading == ReaderNavigation.NONE) ZERO else TITLE_AFTER_NAVIGATION
        val navigationWidth = if (leading == ReaderNavigation.NONE) ReaderSpace.gutter else ReaderSize.appBarNavigation
        val actionSpace = (maxWidth - navigationWidth - titleStart - ReaderSize.touchTarget).coerceAtLeast(ZERO)
        val actionBudget =
            ((actionSpace - ReaderSize.appBarOverflow) / ReaderSize.appBarAction).toInt().coerceAtLeast(0)
        val allActionsWidth =
            ReaderSize.appBarAction * actions.size + if (overflow.isEmpty()) ZERO else ReaderSize.appBarOverflow
        val visibleActions = if (allActionsWidth <= actionSpace) actions else actions.take(actionBudget)
        val menuItems =
            overflow +
                actions.drop(visibleActions.size).map { action ->
                    ReaderMenuItem(action.label, onClick = action.onClick)
                }
        Row(
            modifier =
                Modifier.fillMaxWidth().heightIn(min = ReaderSize.appBar).background(colors.appBar),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (leading) {
                ReaderNavigation.MENU ->
                    NextIconAction(
                        imageVector = ReaderIcons.Menu,
                        contentDescription = stringResource(Res.string.reader_menu),
                        onClick = { onNavigation?.invoke() },
                        tint = colors.onAppBar,
                        modifier = Modifier.size(ReaderSize.appBarNavigation, ReaderSize.appBar),
                    )
                ReaderNavigation.BACK ->
                    NextIconAction(
                        imageVector = ReaderIcons.Back,
                        contentDescription = stringResource(Res.string.reader_back),
                        onClick = { onNavigation?.invoke() },
                        tint = colors.onAppBar,
                        modifier = Modifier.size(ReaderSize.appBarNavigation, ReaderSize.appBar),
                    )
                ReaderNavigation.NONE -> Spacer(Modifier.width(ReaderSpace.gutter))
            }
            // With no leading control the spacer above already brings the title to the gutter.
            Column(modifier = Modifier.weight(1f).padding(start = titleStart)) {
                val titleRow: @Composable () -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = ReaderType.appBarTitle.withReaderFont(),
                            color = colors.onAppBar,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .testTag(ReaderAppBarTags.TITLE)
                                    .weight(1f, fill = false)
                                    .semantics { heading() },
                        )
                        if (onTitleClick != null) {
                            Icon(
                                imageVector = ReaderIcons.ChevronDown,
                                contentDescription = null,
                                tint = colors.onAppBar,
                                modifier = Modifier.padding(start = ReaderSpace.line).size(ReaderSize.icon),
                            )
                        }
                    }
                }
                if (onTitleClick != null) {
                    Box(
                        modifier =
                            Modifier
                                .testTag(ReaderAppBarTags.BOARD_PICKER)
                                .widthIn(min = ReaderSize.touchTarget)
                                .heightIn(min = ReaderSize.appBar)
                                .nextClickable(
                                    role = Role.Button,
                                    onClickLabel = titleClickLabel,
                                    onClick = onTitleClick,
                                ),
                        contentAlignment = Alignment.CenterStart,
                    ) { titleRow() }
                } else if (onTitleTap != null) {
                    Box(
                        modifier =
                            Modifier
                                .widthIn(min = ReaderSize.touchTarget)
                                .heightIn(min = ReaderSize.appBar)
                                .semantics { titleTapLabel?.let { contentDescription = it } }
                                .nextClickable(
                                    role = Role.Button,
                                    onClickLabel = titleTapLabel,
                                    onClick = onTitleTap,
                                ),
                        contentAlignment = Alignment.CenterStart,
                    ) { titleRow() }
                } else {
                    titleRow()
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = ReaderType.appBarSubtitle.withReaderFont(),
                        color = colors.onAppBar,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            visibleActions.forEach { action ->
                NextIconAction(
                    imageVector = action.icon,
                    contentDescription = action.label,
                    onClick = action.onClick,
                    tint = colors.onAppBar,
                    modifier = Modifier.size(ReaderSize.appBarAction, ReaderSize.appBar),
                )
            }
            if (menuItems.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                Box {
                    NextIconAction(
                        imageVector = ReaderIcons.MoreVertical,
                        contentDescription = stringResource(Res.string.reader_more),
                        onClick = { open = true },
                        tint = colors.onAppBar,
                        modifier =
                            Modifier
                                .testTag(
                                    ReaderAppBarTags.OVERFLOW,
                                ).size(48.dp, maxOf(ReaderSize.appBar, 48.dp)),
                    )
                    DropdownMenu(
                        expanded = open,
                        onDismissRequest = { open = false },
                        containerColor = colors.background,
                    ) {
                        menuItems.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = item.label,
                                        style = ReaderType.stateBody.withReaderFont(),
                                        color = if (item.enabled) colors.ink else colors.inkMuted,
                                    )
                                },
                                enabled = item.enabled,
                                onClick = {
                                    open = false
                                    item.onClick()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A full-width divider `size.hairline` thick in `color.hairline`. */
@Composable
internal fun ReaderHairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = ReaderSize.hairline).background(reader.hairline))
}

// The title starts this far after the leading control, which puts it 72dp from the bar's edge.
private val TITLE_AFTER_NAVIGATION = ReaderSpace.gutter
private val ZERO = 0.dp
