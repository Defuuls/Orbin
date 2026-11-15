package com.orbin.uinext.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbin.uinext.LocalOrbinSavedMediaTabVisible
import com.orbin.uinext.OrbinBottomBar
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_library_new_replies
import com.orbin.uinext.resources.reader_library_sections
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderAmoledColors
import com.orbin.uinext.tokens.ReaderSpace
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Shared hosts suppress obsolete content titles and their own system top inset inside this frame. */
val LocalReaderFramePresent = staticCompositionLocalOf { false }

/**
 * How Library pages navigate, provided once by each app's host. [onSelect] opens a section;
 * [onPrimary] opens a bottom-bar destination; the badges feed the Library tab and Watched section.
 */
@Immutable
class LibraryNavigation(
    val onSelect: (LibrarySection) -> Unit,
    val onPrimary: (NextDestination) -> Unit,
    val watchedBadge: Int = 0,
    val downloadBadge: Int = 0,
    /** Back from a Library section where the platform has no system Back (the iOS edge swipe). */
    val onBack: (() -> Unit)? = null,
)

val LocalLibraryNavigation = staticCompositionLocalOf<LibraryNavigation?> { null }

object LibraryTags {
    const val SECTIONS = "library-sections"
    const val SECTION = "library-section-"
}

/**
 * Root destinations keep their content below a fixed app bar; child routes use Back.
 * [onDestination] marks a navigable root; primary navigation itself is the host's bottom bar or rail.
 */
@Suppress("UnusedParameter")
@Composable
fun ReaderScaffold(
    title: String,
    modifier: Modifier = Modifier,
    destination: NextDestination? = null,
    onDestination: ((NextDestination) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    actions: List<ReaderAction> = emptyList(),
    overflow: List<ReaderMenuItem> = emptyList(),
    drawAppBar: Boolean = true,
    immersive: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (LocalReaderFramePresent.current && !drawAppBar) {
        Box(modifier.fillMaxSize()) { content(PaddingValues(bottom = bottomInset())) }
        return
    }
    val colors = reader
    val bodyGround = if (immersive) ReaderAmoledColors.background else colors.background
    val section = LibrarySection.of(destination)
    val library = LocalLibraryNavigation.current.takeIf { section != null }
    val shownTitle = if (library != null) readerDestinationTitle(NextDestination.LIBRARY) else title
    val back = onBack ?: library?.onBack
    Column(
        modifier
            .readerKeyboardShortcuts(
                onBack = back,
                onRefresh = onRefresh ?: actions.firstOrNull { it.icon == ReaderIcons.Refresh }?.onClick,
                onSearch = onSearch ?: actions.firstOrNull { it.icon == ReaderIcons.Search }?.onClick,
            ).readerBackSwipe(back)
            .testTag("reader-frame")
            .fillMaxSize()
            .background(bodyGround)
            .semantics { paneTitle = shownTitle },
    ) {
        if (drawAppBar) {
            ReaderAppBar(
                title = shownTitle,
                // Library is a root tab: Back (system or edge swipe) still returns to Feed, without an arrow.
                navigation = if (onBack != null && library == null) ReaderNavigation.BACK else ReaderNavigation.NONE,
                onNavigation = onBack,
                actions = actions,
                overflow = overflow,
            )
        }
        if (library != null && section != null) {
            LibrarySectionTabs(selected = section, onSelect = library.onSelect, watchedBadge = library.watchedBadge)
        }
        val libraryBar = library?.takeIf { !LocalOrbinNavigationAtSide.current }
        CompositionLocalProvider(LocalReaderFramePresent provides true) {
            Box(Modifier.weight(1f)) {
                content(
                    PaddingValues(
                        bottom =
                            if (libraryBar !=
                                null
                            ) {
                                0.dp
                            } else {
                                bottomInset()
                            },
                    ),
                )
            }
        }
        if (libraryBar != null) {
            OrbinBottomBar(
                selected = NextDestination.LIBRARY,
                onSelect = libraryBar.onPrimary,
                watchedBadge = libraryBar.watchedBadge,
                downloadBadge = libraryBar.downloadBadge,
            )
        }
    }
}

/** Watched, Saved and Downloads as segmented tabs under the Library app bar. */
@Composable
fun LibrarySectionTabs(
    selected: LibrarySection,
    onSelect: (LibrarySection) -> Unit,
    modifier: Modifier = Modifier,
    watchedBadge: Int = 0,
) {
    val colors = reader
    val sectionsLabel = stringResource(Res.string.reader_library_sections)
    val shown =
        LibrarySection.entries.filter {
            it != LibrarySection.DOWNLOADS || LocalOrbinSavedMediaTabVisible.current
        }
    Row(
        modifier
            .testTag(LibraryTags.SECTIONS)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ReaderSpace.gutter, vertical = ReaderSpace.gapSmall)
            .selectableGroup()
            .semantics { paneTitle = sectionsLabel },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEach { section ->
            val isSelected = section == selected
            val label = stringResource(section.label)
            val badge = if (section == LibrarySection.WATCHED) watchedBadge else 0
            val status =
                badge.takeIf { it > 0 }?.let {
                    pluralStringResource(
                        Res.plurals.reader_library_new_replies,
                        it,
                        it,
                    )
                }
            Row(
                Modifier
                    .testTag(LibraryTags.SECTION + section.name)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) colors.accent.copy(alpha = 0.16f) else colors.surfaceVariant)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { if (!isSelected) onSelect(section) },
                    ).semantics {
                        contentDescription = label
                        if (status != null) stateDescription = status
                    }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    section.icon,
                    contentDescription = null,
                    tint = if (isSelected) colors.accent else colors.inkMuted,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    label,
                    style = OrbinType.footnote.withReaderFont().copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) colors.ink else colors.inkMuted,
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                if (badge > 0) {
                    Box(
                        Modifier
                            .heightIn(min = 18.dp)
                            .background(colors.accent, CircleShape)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (badge > 99) "99+" else badge.toString(),
                            style = OrbinType.caption2.copy(fontWeight = FontWeight.ExtraBold),
                            color = colors.onAccent,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                }
            }
        }
    }
}

/** Top-level pages: no Back button. Search is a page opened from the app bar, so it is not one. */
internal fun NextDestination?.isReaderRoot(): Boolean =
    this == NextDestination.FEED ||
        this == NextDestination.BOARDS ||
        this == NextDestination.LIBRARY ||
        LibrarySection.of(this) != null

@Composable
internal fun readerDestinationTitle(destination: NextDestination): String =
    ReaderNavigationSpec.titleOf(destination)?.let { stringResource(it) }.orEmpty()
