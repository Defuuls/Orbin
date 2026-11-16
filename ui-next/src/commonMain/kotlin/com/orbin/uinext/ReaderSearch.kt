package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_search_idle_hint
import com.orbin.uinext.resources.next_search_no_matches
import com.orbin.uinext.resources.next_search_query_label
import com.orbin.uinext.resources.orbin_search_idle_title
import com.orbin.uinext.resources.orbin_search_no_matches_hint
import com.orbin.uinext.resources.reader_search_all
import com.orbin.uinext.resources.reader_search_submit
import com.orbin.uinext.resources.reader_settings_back
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderSpace
import org.jetbrains.compose.resources.stringResource

/** Neko-style search over real source-qualified results, with one retained source filter. */
@Composable
internal fun ReaderSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    state: SearchState,
    onOpenRow: (SearchRow) -> Unit,
    modifier: Modifier,
    onBack: (() -> Unit)?,
    webSearch: (@Composable (String) -> Unit)? = null,
    onWebSearch: ((String) -> Unit)? = null,
) {
    var global by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf("") }
    var submission by rememberSaveable { mutableStateOf(0) }
    var selectedSource by rememberSaveable { mutableStateOf<String?>(null) }
    val searchFocus = remember { FocusRequester() }
    val submitSearch: () -> Unit = {
        if (global) {
            submitted = query.trim()
            submission++
            onWebSearch?.invoke(submitted)
        } else {
            onSearch()
        }
    }
    val rows = (state as? SearchState.Results)?.rows.orEmpty()
    val sources =
        remember(rows) {
            rows.mapNotNull { it.searchSource }.distinct().sortedBy { if (it == "fourchan") "" else it }
        }
    LaunchedEffect(state, sources) {
        if (state is SearchState.Results && selectedSource !in sources) selectedSource = null
    }
    val visibleRows = remember(rows, selectedSource) { filterSearchRows(rows, selectedSource) }

    Column(
        modifier =
            modifier
                .readerKeyboardShortcuts(
                    onBack = onBack,
                    onRefresh = submitSearch,
                    onSearch = { searchFocus.requestFocus() },
                ).readerBackSwipe(onBack)
                .fillMaxSize()
                .background(next.background)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        if (LocalReaderFramePresent.current) {
                            WindowInsetsSides.Horizontal
                        } else {
                            WindowInsetsSides.Top +
                                WindowInsetsSides.Horizontal
                        },
                    ),
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (webSearch != null) {
            PlatformSegments(
                labels = listOf("Followed boards", "All of 4chan"),
                selected = if (global) 1 else 0,
                onSelect = { global = it == 1 },
            )
        }
        SearchNavigationBar(query, onQueryChange, submitSearch, onBack, searchFocus)
        if (!global && state is SearchState.Results && sources.isNotEmpty()) {
            SearchSourceTabs(sources, selectedSource) { selectedSource = it }
        }
        Box(Modifier.weight(1f)) {
            if (global && webSearch != null) {
                key(if (onWebSearch == null) submission else 0) { webSearch(submitted) }
            } else {
                ReaderSearchResults(
                    state = if (state is SearchState.Results) SearchState.Results(visibleRows) else state,
                    onOpenRow = onOpenRow,
                    onRetry = onSearch,
                )
            }
        }
    }
}

@Composable
private fun SearchNavigationBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: (() -> Unit)?,
    searchFocus: FocusRequester,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        onSearch()
    }
    BoxWithConstraints(
        Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = ReaderSpace.gutter, vertical = 4.dp),
    ) {
        val stackAction = maxWidth < 360.dp && LocalDensity.current.fontScale >= 1.6f
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onBack != null && !LocalReaderFramePresent.current) {
                    NextIconAction(
                        ReaderIcons.Back,
                        stringResource(Res.string.reader_settings_back),
                        onBack,
                        tint = next.ink,
                    )
                }
                SchematicSearch(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(Res.string.next_search_query_label),
                    onSearch = submit,
                    modifier = Modifier.weight(1f).focusRequester(searchFocus),
                    shape = RoundedCornerShape(12.dp),
                )
                if (!stackAction) SearchSubmit(submit)
            }
            if (stackAction) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SearchSubmit(submit) }
            }
        }
    }
}

@Composable
private fun SearchSubmit(onSearch: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = MIN_TOUCH_TARGET)
            .widthIn(min = 64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(reader.accent.copy(alpha = 0.12f))
            .nextClickable(role = Role.Button, onClick = onSearch)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.reader_search_submit),
            style =
                OrbinType.body.withReaderFont().copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
            color = reader.accent,
        )
    }
}

@Composable
private fun SearchSourceTabs(
    sources: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(horizontal = ReaderSpace.gutter, vertical = 4.dp)
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val tabs = listOf(null to stringResource(Res.string.reader_search_all)) + sources.map { it to sourceLabel(it) }
        val accent = reader.accent
        val idle = reader.surfaceVariant
        val activeFill =
            remember(accent) {
                Brush.linearGradient(listOf(accent.copy(alpha = 0.18f), Color(0xFFA78BFA).copy(alpha = 0.14f)))
            }
        val idleFill = remember(idle) { Brush.linearGradient(listOf(idle, idle)) }
        tabs.forEach { (source, label) ->
            val active = source == selected
            Box(
                Modifier
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) activeFill else idleFill)
                    .border(1.dp, reader.hairline, RoundedCornerShape(14.dp))
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(source) })
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style =
                        OrbinType.body.withReaderFont().copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                    color = if (active) reader.ink else reader.inkSecondary,
                )
            }
        }
    }
}

@Composable
internal fun ReaderSearchResults(
    state: SearchState,
    onOpenRow: (SearchRow) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        SearchState.Idle ->
            ReaderEmptyState(
                title = stringResource(Res.string.orbin_search_idle_title),
                message = stringResource(Res.string.next_search_idle_hint),
            )
        SearchState.Loading -> NextLoading()
        is SearchState.Error -> ReaderErrorState(ReaderFailureCause.UNKNOWN, message = state.message, onRetry = onRetry)
        is SearchState.Results ->
            if (state.rows.isEmpty()) {
                ReaderEmptyState(
                    title = stringResource(Res.string.next_search_no_matches),
                    message = stringResource(Res.string.orbin_search_no_matches_hint),
                )
            } else {
                Box(Modifier.fillMaxSize()) {
                    val wideWindow = readerRecordUsesWideText()
                    LazyColumn(
                        modifier =
                            Modifier
                                .widthIn(
                                    max = 720.dp,
                                ).fillMaxSize()
                                .align(Alignment.TopCenter)
                                .testTag(ReaderSearchTags.LIST),
                        contentPadding =
                            PaddingValues(
                                start = ReaderSpace.gutter,
                                end = ReaderSpace.gutter,
                                top = 8.dp,
                                bottom = 8.dp + bottomInset(),
                            ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.rows, key = { it.id }) { row ->
                            SearchResultRow(row, wideWindow, onClick = { onOpenRow(row) })
                        }
                    }
                }
            }
    }
}

object ReaderSearchTags {
    const val LIST = "reader-search-results"

    fun row(id: String): String = "reader-search-row:$id"
}

@Composable
private fun SearchResultRow(
    row: SearchRow,
    wideWindow: Boolean,
    onClick: () -> Unit,
) {
    ReaderRecordRow(
        title = row.title,
        source = row.board,
        detail = row.snippet,
        wideWindow = wideWindow,
        onOpen = onClick,
        modifier = Modifier.testTag(ReaderSearchTags.row(row.id)),
    )
}

/** A source tab filters complete qualified identities without coalescing equal thread numbers. */
internal fun filterSearchRows(
    rows: List<SearchRow>,
    source: String?,
): List<SearchRow> = if (source == null) rows else rows.filter { it.searchSource == source }

private val SearchRow.searchSource: String?
    get() {
        val parts = id.split('/')
        if (parts.size < 3 || parts.take(3).any(String::isBlank)) return null
        return when (val source = parts[0].lowercase()) {
            "4chan" -> "fourchan"
            else -> source
        }
    }
