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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_drawer
import com.orbin.uinext.resources.reader_nav_boards
import com.orbin.uinext.resources.reader_nav_home
import com.orbin.uinext.resources.reader_nav_saved
import com.orbin.uinext.resources.reader_nav_search
import com.orbin.uinext.resources.reader_nav_settings
import com.orbin.uinext.resources.reader_nav_watched
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderAmoledColors
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Shared hosts suppress obsolete content titles and their own system top inset inside this frame. */
val LocalReaderFramePresent = staticCompositionLocalOf { false }

/** Catalogs draw their own app bar while the containing frame owns the drawer. */
val LocalReaderOpenDrawer = staticCompositionLocalOf<(() -> Unit)?> { null }

object ReaderDrawerTags {
    const val DRAWER = "reader-navigation-drawer"
    const val ENTRY = "reader-navigation-entry-"
    const val ITEM_PREFIX = ENTRY
}

/** Root destinations keep their content below a fixed app bar; child routes use Back. */
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
    drawerDestinations: Set<NextDestination>? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (LocalReaderFramePresent.current && !drawAppBar) {
        Box(modifier.fillMaxSize()) { content(PaddingValues(bottom = bottomInset())) }
        return
    }
    val colors = reader
    val bodyGround = if (immersive) ReaderAmoledColors.background else colors.background
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val hasDrawer = onBack == null && onDestination != null && destination.isReaderRoot()
    val openDrawer: () -> Unit = { scope.launch(start = CoroutineStart.UNDISPATCHED) { drawerState.open() } }
    val closeDrawer: () -> Unit = { scope.launch(start = CoroutineStart.UNDISPATCHED) { drawerState.close() } }
    val drawerVisible = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open
    BoxWithConstraints(modifier.testTag("reader-frame").fillMaxSize().semantics { paneTitle = title }) {
        val drawerWidth = minOf(ReaderSize.drawerMax, (maxWidth - ReaderSize.appBar).coerceAtLeast(0.dp))
        ModalNavigationDrawer(
            drawerState = drawerState,
            // On iOS a closed drawer never steals the leading-edge back gesture. Material also
            // gates scrim dismissal on this flag, so enable dismissal once the drawer is opened.
            gesturesEnabled = hasDrawer && (LocalNextPlatform.current == NextPlatform.ANDROID || drawerVisible),
            drawerContent = {
                if (hasDrawer) {
                    ModalDrawerSheet(
                        modifier =
                            Modifier
                                .testTag(ReaderDrawerTags.DRAWER)
                                .width(drawerWidth)
                                .then(if (drawerVisible) Modifier else Modifier.clearAndSetSemantics {}),
                        drawerShape = RectangleShape,
                        // Match Clover's darker primary color behind the consumed status inset.
                        drawerContainerColor = colors.statusBar,
                        drawerContentColor = colors.ink,
                        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    bodyGround,
                                ).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                        ) {
                            ReaderDrawer(
                                selected = destination,
                                destinations = drawerDestinations,
                                onSelect = { selected ->
                                    scope.launch {
                                        drawerState.close()
                                        onDestination(selected)
                                    }
                                },
                            )
                        }
                    }
                }
            },
        ) {
            Column(
                Modifier.fillMaxSize().background(
                    bodyGround,
                ),
            ) {
                if (drawAppBar) {
                    ReaderAppBar(
                        title = title,
                        navigation =
                            when {
                                onBack != null -> ReaderNavigation.BACK
                                hasDrawer -> ReaderNavigation.MENU
                                else -> ReaderNavigation.NONE
                            },
                        onNavigation = onBack ?: openDrawer,
                        actions = actions,
                        overflow = overflow,
                    )
                }
                CompositionLocalProvider(
                    LocalReaderFramePresent provides true,
                    LocalReaderOpenDrawer provides openDrawer.takeIf { hasDrawer },
                ) {
                    Box(Modifier.weight(1f)) { content(PaddingValues(bottom = bottomInset())) }
                }
            }
        }
        ReaderDrawerBackHandler(enabled = drawerVisible, onBack = closeDrawer)
    }
}

/** Drawer entries use one shared ordering and real destination icons on both platforms. */
@Composable
fun ReaderDrawer(
    selected: NextDestination?,
    onSelect: (NextDestination) -> Unit,
    modifier: Modifier = Modifier,
    destinations: Set<NextDestination>? = null,
) {
    val drawerTitle = stringResource(Res.string.reader_drawer)
    Column(
        modifier
            .fillMaxSize()
            .background(reader.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
            .verticalScroll(rememberScrollState())
            .padding(vertical = ReaderSpace.gapSmall)
            .semantics { paneTitle = drawerTitle },
    ) {
        readerDrawerEntries().filter { destinations == null || it.destination in destinations }.forEach { entry ->
            if (entry.destination == NextDestination.SETTINGS) {
                ReaderHairline(Modifier.padding(vertical = ReaderSpace.gapSmall))
            }
            Row(
                Modifier
                    .testTag(ReaderDrawerTags.ENTRY + entry.destination.name)
                    .fillMaxWidth()
                    .heightIn(min = ReaderSize.touchTarget)
                    .background(if (entry.destination == selected) reader.selected else reader.background)
                    .selectable(
                        selected = entry.destination == selected,
                        role = Role.Tab,
                        onClick = { onSelect(entry.destination) },
                    ).padding(horizontal = ReaderSpace.gutter, vertical = ReaderSpace.line),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReaderSpace.gap),
            ) {
                Icon(
                    entry.icon,
                    contentDescription = null,
                    tint = reader.ink,
                    modifier = Modifier.size(ReaderSize.icon),
                )
                Text(entry.label, style = OrbinType.stateBody.withReaderFont(), color = reader.ink)
            }
        }
    }
}

private data class DrawerEntry(
    val destination: NextDestination,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun readerDrawerEntries(): List<DrawerEntry> =
    listOf(
        DrawerEntry(NextDestination.FEED, stringResource(Res.string.reader_nav_home), ReaderIcons.Home),
        DrawerEntry(NextDestination.WATCHED, stringResource(Res.string.reader_nav_watched), ReaderIcons.Watched),
        DrawerEntry(NextDestination.BOARDS, stringResource(Res.string.reader_nav_boards), ReaderIcons.Boards),
        DrawerEntry(NextDestination.SEARCH, stringResource(Res.string.reader_nav_search), ReaderIcons.Search),
        DrawerEntry(NextDestination.SAVED, stringResource(Res.string.reader_nav_saved), ReaderIcons.Saved),
        DrawerEntry(NextDestination.SETTINGS, stringResource(Res.string.reader_nav_settings), ReaderIcons.Settings),
    )

internal fun NextDestination?.isReaderRoot(): Boolean =
    this == NextDestination.FEED ||
        this == NextDestination.WATCHED ||
        this == NextDestination.BOARDS ||
        this == NextDestination.SEARCH ||
        this == NextDestination.SAVED

@Composable
internal fun readerDestinationTitle(destination: NextDestination): String =
    readerDrawerEntries()
        .firstOrNull {
            it.destination ==
                destination
        }?.label
        .orEmpty()
